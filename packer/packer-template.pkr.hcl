packer {
  required_plugins {
    amazon = {
      version = ">= 1.0.0"
      source  = "github.com/hashicorp/amazon"
    }
  }
}

variable "ami_name" {
  default = "webapp-ami"
}

source "amazon-ebs" "ubuntu" {
  ami_name      = var.ami_name
  instance_type = "t2.micro"
  region        = "us-east-1"
  ssh_username = "ubuntu"
  source_ami    = "ami-0866a3c8686eaeeba"
}

build {
  sources = ["source.amazon-ebs.ubuntu"]

  provisioner "shell" {
    inline = [
      # Update the package manager and upgrade existing packages
      "sudo apt-get update",
      "sudo apt-get upgrade -y",

      # Install OpenJDK 17
      "sudo apt-get install -y openjdk-17-jdk"
    ]
  }

  # Save the build manifest as a JSON file
  post-processor "manifest" {
    output = "packer-manifest.json"
  }
}
