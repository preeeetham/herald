terraform {
  required_version = ">= 1.9"

  required_providers {
    aws    = { source = "hashicorp/aws", version = "~> 6.74" }
    random = { source = "hashicorp/random", version = "~> 3.9" }
  }

  # Terraform state contains generated secrets: keep it in an encrypted, access-controlled backend.
  # backend "s3" {
  #   bucket       = "<state-bucket>"
  #   key          = "herald/terraform.tfstate"
  #   region       = "ap-south-1"
  #   encrypt      = true
  #   use_lockfile = true
  # }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "herald"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
