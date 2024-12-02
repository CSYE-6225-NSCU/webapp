packer {
  required_plugins {
    amazon = {
      source  = "github.com/hashicorp/amazon"
      version = "~> 1"
    }
  }
}

variable "artifact_path" {
  description = "The path to the application artifact JAR file"
  type        = string
}

variable "ami_name_prefix" {
  description = "Prefix for the AMI name"
  type        = string
  default     = "webapp-ami"
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
}

variable "source_ami" {
  description = "EC2 source AMI id"
  type        = string
  default     = "ami-0866a3c8686eaeeba"
}

locals {
  ami_name = "${var.ami_name_prefix}-${formatdate("YYYYMMDD-HHmm", timestamp())}"
}

source "amazon-ebs" "ubuntu" {
  instance_type = var.instance_type
  ami_name      = local.ami_name
  ssh_username  = "ubuntu"
  source_ami    = var.source_ami
}

build {
  sources = ["source.amazon-ebs.ubuntu"]

  # 1. Install Java 17, PostgreSQL, unzip, curl, jq, and AWS CLI
  provisioner "shell" {
    inline = [
      "sudo apt-get update",
      "sudo apt-get upgrade -y",
      "sudo apt-get install -y openjdk-17-jdk unzip curl jq",

      # Install AWS CLI v2
      "curl \"https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip\" -o \"awscliv2.zip\"",
      "unzip awscliv2.zip",
      "sudo ./aws/install",
    ]
  }

  # 2. Create the non-login user csye6225 and set directory permissions
  provisioner "shell" {
    inline = [
      "sudo adduser --system --group --no-create-home --shell /usr/sbin/nologin csye6225",
      "sudo mkdir -p /opt/myapp",
      "sudo chown csye6225:csye6225 /opt/myapp",
      "sudo chmod 755 /opt/myapp"
    ]
  }

  # 3. Copy the JAR from the local file to the instance
  provisioner "file" {
    source      = var.artifact_path
    destination = "/tmp/webapp.jar"
  }

  provisioner "shell" {
    inline = [
      "sudo mv /tmp/webapp.jar /opt/myapp/webapp.jar",
      "sudo chown csye6225:csye6225 /opt/myapp/webapp.jar",
      "sudo chmod 755 /opt/myapp/webapp.jar"
    ]
  }

  # 4. Configure the systemd service (user data will set environment variables)
  provisioner "shell" {
    inline = [
      # Create a systemd service to run the application
      "sudo bash -c 'cat <<EOF > /etc/systemd/system/webapp.service\n[Unit]\nDescription=CSYE6225 WebApp\nAfter=network.target\n\n[Service]\nUser=csye6225\nEnvironmentFile=/etc/environment\nExecStart=/usr/bin/java -jar /opt/myapp/webapp.jar\nWorkingDirectory=/opt/myapp\nRestart=always\n\n[Install]\nWantedBy=multi-user.target\nEOF'",

      # Reload systemd and enable the service
      "sudo systemctl daemon-reload",
      "sudo systemctl enable webapp.service"
    ]
  }

  provisioner "shell" {
    inline = [
      "sudo mkdir -p /var/log/myapp",
      "sudo chown csye6225:csye6225 /var/log/myapp",
      "sudo chmod 755 /var/log/myapp"
    ]
  }

  # 5. Install and Configure CloudWatch Agent
  provisioner "shell" {
    inline = [
      # Download and install the CloudWatch Agent
      "cd /tmp",
      "curl -O https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb",
      "sudo dpkg -i -E ./amazon-cloudwatch-agent.deb",

      # Create the CloudWatch Agent configuration file
      "sudo tee /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json <<EOF\n{\n  \"logs\": {\n    \"logs_collected\": {\n      \"files\": {\n        \"collect_list\": [\n          {\n            \"file_path\": \"/var/log/myapp/application.log\",\n            \"log_group_name\": \"myapp-log-group\",\n            \"log_stream_name\": \"{instance_id}\",\n            \"timezone\": \"UTC\"\n          }\n        ]\n      }\n    }\n  },\n  \"metrics\": {\n    \"namespace\": \"WebApp\",\n    \"metrics_collected\": {\n      \"statsd\": {\n        \"service_address\": \"localhost:8125\"\n      }\n    }\n  }\n}\nEOF",

      # Start the CloudWatch Agent with the configuration
      "sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json -s"
    ]
  }
}
