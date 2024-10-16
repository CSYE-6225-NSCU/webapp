packer {
  required_plugins {
    amazon = {
      version = ">= 1.0.0"
      source  = "github.com/hashicorp/amazon"
    }
  }
}

variable "ami_name" {
  default = "webapp-custom-ami"
}

source "amazon-ebs" "ubuntu" {
  ami_name      = "webapp-custom-ami"
  instance_type = "t2.micro"
  region        = "us-east-1"
  source_ami_filter {
    filters = {
      "name"                = "ubuntu/images/hvm-ssd/ubuntu-focal-20.04-amd64-server-*"
      "virtualization-type" = "hvm"
      "root-device-type"    = "ebs"
    }
    owners      = ["099720109477"]
    most_recent = true
  }
  ssh_username = "ubuntu"
}

build {
  sources = ["source.amazon-ebs.ubuntu"]

  provisioner "shell" {
    inline = [
      # Update the package manager and upgrade existing packages
      "sudo apt-get update --fix-missing",
      "sudo apt-get upgrade -y",

      # Add the PostgreSQL 16 repository and install PostgreSQL 16
      "wget -qO - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -",
      "sudo sh -c 'echo \"deb http://apt.postgresql.org/pub/repos/apt/ focal-pgdg main\" > /etc/apt/sources.list.d/pgdg.list'",
      "sudo apt-get update",
      "sudo apt-get install -y postgresql-16 postgresql-contrib",

      # Install OpenJDK 17
      "sudo apt-get install -y openjdk-17-jdk openjdk-17-jre",

      # Clean up apt cache to reduce image size and fix any broken packages
      "sudo apt-get clean",
      "sudo apt-get install -f"
    ]
  }

  # Save the build manifest as a JSON file
  post-processor "manifest" {
    output = "packer-manifest.json"
  }
}
