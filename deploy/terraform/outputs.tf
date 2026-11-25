output "cluster_name" {
  value = module.eks.cluster_name
}

output "configure_kubectl" {
  value = "aws eks update-kubeconfig --region ${var.region} --name ${module.eks.cluster_name}"
}

output "database_jdbc_url" {
  value = "jdbc:postgresql://${aws_db_instance.postgres.endpoint}/herald?sslmode=require"
}

output "database_password_secret_arn" {
  description = "Secrets Manager secret holding the RDS master password (managed by RDS)."
  value       = aws_db_instance.postgres.master_user_secret[0].secret_arn
}

output "redis_host" {
  value = aws_elasticache_replication_group.cache.primary_endpoint_address
}

output "sqs_queue_url" {
  value = aws_sqs_queue.deliveries.url
}

output "app_secret_arn" {
  value = aws_secretsmanager_secret.app.arn
}

output "workload_role_arn" {
  description = "Annotate the herald service account with this role (eks.amazonaws.com/role-arn)."
  value       = aws_iam_role.workload.arn
}

output "ecr_repositories" {
  value = { for k, r in aws_ecr_repository.service : k => r.repository_url }
}
