# Packer Configuration

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
  type        = strings
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
  ami_name = "${var.ami_name_prefix}-${formatdate("20060102-150405", timestamp())}"
}



source "amazon-ebs" "ubuntu" {
  region        = var.region
  instance_type = var.instance_type
  ami_name      = local.ami_name
  ssh_username  = "ubuntu"
  source_ami    = "ami-0866a3c8686eaeeba"

}


build {
  sources = ["source.amazon-ebs.ubuntu"]

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

  provisioner "shell" {
    inline = [
      "sudo adduser --system --group --no-create-home --shell /usr/sbin/nologin csye6225",
      "sudo mkdir -p /opt/myapp",
      "sudo chown csye6225:csye6225 /opt/myapp",
      "sudo chmod 755 /opt/myapp"
    ]
  }

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


  provisioner "shell" {
    inline = [
      "sudo bash -c 'cat <<EOF > /etc/systemd/system/webapp.service\n[Unit]\nDescription=Web Application Service\nAfter=network.target\n\n[Service]\nUser=csye6225\nGroup=csye6225\nEnvironmentFile=/etc/environment\nExecStart=/usr/bin/java -jar /opt/myapp/webapp.jar\nSuccessExitStatus=143\nRestart=on-failure\n\n[Install]\nWantedBy=multi-user.target\nEOF'",
      "sudo systemctl daemon-reload",
      "sudo systemctl enable webapp.service",
      "sudo systemctl start webapp.service"
    ]
  }

  provisioner "shell" {
    inline = [
      "sudo apt-get clean",
      "sudo rm -rf /var/lib/apt/lists/*"
    ]
  }

  post-processor "manifest" {
    output = "manifest.json"
  }
}
