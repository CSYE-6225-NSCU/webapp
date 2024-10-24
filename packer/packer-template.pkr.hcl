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
  default     = "t2.micro" # Added default for safety
}

variable "region" {
  description = "AWS region to build the AMI in"
  type        = string
  default     = "us-east-1"
}

# Locals

locals {
  ami_name = "${var.ami_name_prefix}-${formatdate("20060102-1504", timestamp())}"
}

# Source Configuration

source "amazon-ebs" "ubuntu" {
  region        = var.region
  instance_type = var.instance_type
  ami_name      = local.ami_name
  ssh_username  = "ubuntu"
  source_ami    = "ami-0866a3c8686eaeeba" # Ensure this AMI ID is correct and available in the specified region

  # Optional: Specify additional configurations like VPC ID, subnet ID, etc., if needed
}

# Build Configuration

build {
  sources = ["source.amazon-ebs.ubuntu"]

  # 1. Install Java 17 and Update System
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

  # 2. Create Non-Login User and Set Directory Permissions
  provisioner "shell" {
    inline = [
      "sudo adduser --system --group --no-create-home --shell /usr/sbin/nologin csye6225",
      "sudo mkdir -p /opt/myapp",
      "sudo chown csye6225:csye6225 /opt/myapp",
      "sudo chmod 755 /opt/myapp"
    ]
  }

  # 3. Copy the JAR from the Local File to the Instance
  provisioner "file" {
    source      = var.artifact_path
    destination = "/tmp/webapp.jar"
  }

  # 4. Move and Set Permissions for the JAR
  provisioner "shell" {
    inline = [
      "sudo mv /tmp/webapp.jar /opt/myapp/webapp.jar",
      "sudo chown csye6225:csye6225 /opt/myapp/webapp.jar",
      "sudo chmod 755 /opt/myapp/webapp.jar"
    ]
  }

  # 5. Set Environment Variables and Configure the Systemd Service
  provisioner "shell" {
    inline = [
      "sudo bash -c 'cat <<EOF > /etc/systemd/system/webapp.service\n[Unit]\nDescription=Web Application Service\nAfter=network.target\n\n[Service]\nUser=csye6225\nGroup=csye6225\nEnvironmentFile=/etc/environment\nExecStart=/usr/bin/java -jar /opt/myapp/webapp.jar\nSuccessExitStatus=143\nRestart=on-failure\n\n[Install]\nWantedBy=multi-user.target\nEOF'",
      "sudo systemctl daemon-reload",
      "sudo systemctl enable webapp.service",
      "sudo systemctl start webapp.service" # Optionally start the service immediately
    ]
  }

  # 6. Clean Up APT Cache
  provisioner "shell" {
    inline = [
      "sudo apt-get clean",
      "sudo rm -rf /var/lib/apt/lists/*"
    ]
  }

  # Post-Processor: Generate Manifest
  post-processor "manifest" {
    output = "manifest.json"
  }
}
