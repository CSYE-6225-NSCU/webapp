# packer-template.pkr.hcl

# Packer Configuration

packer {
  required_plugins {
    amazon = {
      source  = "github.com/hashicorp/amazon"
      version = "~> 1.0"
    }
  }
}

variable "ami_name_prefix" {
  description = "Prefix for the AMI name"
  type        = string
  default     = "webapp-ami"
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t2.micro"
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
  source_ami_filter {
    filters = {
      virtualization-type = "hvm"
      name                = "ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"
      root-device-type    = "ebs"
    }
    owners      = ["099720109477"] # Canonical
    most_recent = true
  }
}

# Build Steps
build {
  sources = ["source.amazon-ebs.ubuntu"]

  # 1. Update and install necessary packages
  provisioner "shell" {
    name = "Update and Install Packages"
    inline = [
      "export DEBIAN_FRONTEND=noninteractive",
      "sudo apt-get update -y",
      "sudo apt-get upgrade -y",
      "sudo apt-get install -y software-properties-common",
      "sudo add-apt-repository universe -y",
      "sudo add-apt-repository multiverse -y",
      "sudo apt-get update -y",
      "sudo apt-get install -y openjdk-17-jdk-headless wget unzip"
    ]
  }

  # 2. Create dedicated user and application directory
  provisioner "shell" {
    name = "Create User and Application Directory"
    inline = [
      "sudo adduser --system --group --no-create-home --shell /usr/sbin/nologin csye6225",
      "sudo mkdir -p /opt/myapp",
      "sudo chown csye6225:csye6225 /opt/myapp",
      "sudo chmod 755 /opt/myapp"
    ]
  }

  # After creating the application directory
  provisioner "shell" {
    name = "Create Logs Directory"
    inline = [
      "sudo mkdir -p /opt/myapp/logs",
      "sudo chown csye6225:csye6225 /opt/myapp/logs",
      "sudo chmod 755 /opt/myapp/logs"
    ]
  }


  # 3. Upload the application artifact (JAR file)
  provisioner "file" {
    name        = "Upload Application JAR"
    source      = "../artifact/webapp.jar"
    destination = "/tmp/webapp.jar"
  }

  # 4. Move the application artifact to the application directory
  provisioner "shell" {
    name = "Deploy Application JAR"
    inline = [
      "sudo mv /tmp/webapp.jar /opt/myapp/webapp.jar",
      "sudo chown csye6225:csye6225 /opt/myapp/webapp.jar",
      "sudo chmod 755 /opt/myapp/webapp.jar"
    ]
  }

  # 5. Create and enable the SystemD service for the application
  provisioner "shell" {
    name = "Create SystemD Service"
    inline = [
      "sudo bash -c 'cat <<EOF > /etc/systemd/system/webapp.service\n[Unit]\nDescription=Web Application Service\nAfter=network.target\n\n[Service]\nUser=csye6225\nGroup=csye6225\nEnvironmentFile=/etc/environment\nExecStart=/usr/bin/java -jar /opt/myapp/webapp.jar\nSuccessExitStatus=143\nRestart=on-failure\n\n[Install]\nWantedBy=multi-user.target\nEOF'",
      "sudo systemctl daemon-reload",
      "sudo systemctl enable webapp.service",
      "sudo systemctl start webapp.service"
    ]
  }

  # 6. Install CloudWatch Agent via deb package
  provisioner "shell" {
    name = "Install CloudWatch Agent"
    inline = [
      "wget https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb",
      "sudo dpkg -i -E ./amazon-cloudwatch-agent.deb",
      "rm -f amazon-cloudwatch-agent.deb"
    ]
  }

  # 7. Upload CloudWatch Agent configuration file
  provisioner "file" {
    name        = "Upload CloudWatch Config"
    source      = "../artifact/cloudwatch-config.json"
    destination = "/tmp/amazon-cloudwatch-agent.json"
  }

  # 8. Move the configuration file to /opt and set permissions
  provisioner "shell" {
    name = "Configure CloudWatch Agent"
    inline = [
      "sudo mkdir -p /opt/aws/amazon-cloudwatch-agent/etc",
      "sudo mv /tmp/amazon-cloudwatch-agent.json /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json",
      "sudo chown root:root /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json",
      "sudo chmod 644 /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json",
      "sudo systemctl daemon-reload"
    ]
  }

  # 9. Configure and start the CloudWatch Agent
  provisioner "shell" {
    name = "Start CloudWatch Agent"
    inline = [
      "sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json -s",
      "sudo systemctl enable amazon-cloudwatch-agent",
      "sudo systemctl start amazon-cloudwatch-agent"
    ]
  }

  # 10. Clean up APT cache
  provisioner "shell" {
    name = "Clean Up APT Cache"
    inline = [
      "sudo apt-get clean",
      "sudo rm -rf /var/lib/apt/lists/*"
    ]
  }

  # 11. Verify installation (optional but recommended)
  provisioner "shell" {
    name = "Verify Installations"
    inline = [
      "java -version",
      "sudo systemctl status webapp.service",
      "sudo systemctl status amazon-cloudwatch-agent",
      "ls -l /opt/myapp/webapp.jar",
      "cat /etc/systemd/system/webapp.service"
    ]
  }

  # Post-processor to generate manifest
  post-processor "manifest" {
    output = "manifest.json"
  }
}
