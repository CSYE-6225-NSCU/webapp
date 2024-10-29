# packer-template.pkr.hcl

# Packer Configuration

packer {
  required_plugins {
    amazon = {
      source  = "github.com/hashicorp/amazon"
      version = "~> 1"
    }
  }
}

# Variables
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

variable "region" {
  description = "AWS region to build the AMI in"
  type        = string
  default     = "us-east-1"
}

locals {
  ami_name = "${var.ami_name_prefix}-${formatdate("YYYYMMDD-HHmm", timestamp())}"
}

# Source AMI
source "amazon-ebs" "ubuntu" {
  region        = var.region
  instance_type = var.instance_type
  ami_name      = local.ami_name
  ssh_username  = "ubuntu"
  source_ami    = "ami-0866a3c8686eaeeba" # Ubuntu 22.04 LTS AMI ID
}

# Build Steps
build {
  sources = ["source.amazon-ebs.ubuntu"]

  # Update and install necessary packages
  provisioner "shell" {
    inline = [
      "export DEBIAN_FRONTEND=noninteractive",
      "sudo timedatectl set-ntp true",
      "sudo timedatectl set-timezone UTC",
      "sudo apt-get update -y",
      "sudo apt-get upgrade -y",
      "sudo apt-get install -y openjdk-17-jdk-headless"
    ]
  }

  # Create dedicated user and application directory
  provisioner "shell" {
    inline = [
      "sudo adduser --system --group --no-create-home --shell /usr/sbin/nologin csye6225",
      "sudo mkdir -p /opt/myapp",
      "sudo chown csye6225:csye6225 /opt/myapp",
      "sudo chmod 755 /opt/myapp"
    ]
  }

  # Upload the application artifact (JAR file)
  provisioner "file" {
    source      = "../artifact/webapp.jar"
    destination = "/tmp/webapp.jar"
  }

  # Move the application artifact to the application directory
  provisioner "shell" {
    inline = [
      "sudo mv /tmp/webapp.jar /opt/myapp/webapp.jar",
      "sudo chown csye6225:csye6225 /opt/myapp/webapp.jar",
      "sudo chmod 755 /opt/myapp/webapp.jar"
    ]
  }

  # Create and enable the SystemD service for the application
  provisioner "shell" {
    inline = [
      "sudo bash -c 'cat <<EOF > /etc/systemd/system/webapp.service\n[Unit]\nDescription=Web Application Service\nAfter=network.target\n\n[Service]\nUser=csye6225\nGroup=csye6225\nEnvironmentFile=/etc/environment\nExecStart=/usr/bin/java -jar /opt/myapp/webapp.jar\nSuccessExitStatus=143\nRestart=on-failure\n\n[Install]\nWantedBy=multi-user.target\nEOF'",
      "sudo systemctl daemon-reload",
      "sudo systemctl enable webapp.service",
      "sudo systemctl start webapp.service"
    ]
  }

  # Clean up APT cache
  provisioner "shell" {
    inline = [
      "sudo apt-get clean",
      "sudo rm -rf /var/lib/apt/lists/*"
    ]
  }

  # Install CloudWatch Agent via deb package
  provisioner "shell" {
    inline = [
      "wget https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb",
      "sudo dpkg -i -E ./amazon-cloudwatch-agent.deb",
      "rm -f amazon-cloudwatch-agent.deb"
    ]
  }

  # Upload CloudWatch Agent configuration file to /tmp
  provisioner "file" {
    source      = "../artifact/cloudwatch-config.json"
    destination = "/tmp/amazon-cloudwatch-agent.json"
  }

  # Verify the file was uploaded
  provisioner "shell" {
    inline = [
      "ls -l /tmp/amazon-cloudwatch-agent.json",
      "cat /tmp/amazon-cloudwatch-agent.json"
    ]
  }

  # Move the configuration file to /opt and set permissions
  provisioner "shell" {
    inline = [
      "sudo mkdir -p /opt/aws/amazon-cloudwatch-agent/etc",
      "sudo mv /tmp/amazon-cloudwatch-agent.json /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json",
      "sudo chown root:root /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json",
      "sudo chmod 644 /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json",
      "ls -l /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json",
      "cat /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json"
    ]
  }

  # Configure and start the CloudWatch Agent
  provisioner "shell" {
    inline = [
      "sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \\",
      "  -a stop",
      "sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \\",
      "  -a fetch-config \\",
      "  -m ec2 \\",
      "  -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json \\",
      "  -s",
      "sudo systemctl enable amazon-cloudwatch-agent"
    ]
  }

  # Post-processor to generate manifest
  post-processor "manifest" {
    output = "manifest.json"
  }
}
