variable "region" {
  description = "AWS region (ap-south-1 = Mumbai, closest to Bangalore)."
  type        = string
  default     = "ap-south-1"
}

variable "environment" {
  description = "Environment name, e.g. staging or prod."
  type        = string
  default     = "staging"
}

variable "vpc_cidr" {
  type    = string
  default = "10.40.0.0/16"
}

variable "single_nat_gateway" {
  description = "One NAT gateway instead of one per AZ — cheaper, but a single point of failure for egress."
  type        = bool
  default     = true
}

variable "kubernetes_version" {
  type    = string
  default = "1.34"
}

variable "node_instance_types" {
  type    = list(string)
  default = ["t4g.large"]
}

variable "node_group_size" {
  type    = object({ min = number, desired = number, max = number })
  default = { min = 2, desired = 3, max = 6 }
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.medium"
}

variable "db_multi_az" {
  type    = bool
  default = false
}

variable "cache_node_type" {
  type    = string
  default = "cache.t4g.small"
}

variable "deletion_protection" {
  description = "Protect the database from accidental deletion. Disable only for throwaway environments."
  type        = bool
  default     = true
}
