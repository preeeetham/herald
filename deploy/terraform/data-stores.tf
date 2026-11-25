# ---------------------------------------------------------------------------
# PostgreSQL (source of truth + transactional outbox)
# ---------------------------------------------------------------------------
resource "aws_security_group" "postgres" {
  name_prefix = "${local.name}-postgres-"
  description = "PostgreSQL reachable only from EKS nodes"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "postgres_from_nodes" {
  security_group_id            = aws_security_group.postgres.id
  referenced_security_group_id = module.eks.node_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}

resource "aws_db_parameter_group" "postgres" {
  name_prefix = "${local.name}-pg17-"
  family      = "postgres17"

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
}

resource "aws_db_instance" "postgres" {
  identifier_prefix = "${local.name}-"
  engine            = "postgres"
  engine_version    = "17"
  instance_class    = var.db_instance_class

  db_name  = "herald"
  username = "herald"
  # RDS generates and rotates the password in Secrets Manager; it never appears in Terraform state.
  manage_master_user_password = true

  allocated_storage     = 50
  max_allocated_storage = 500
  storage_type          = "gp3"
  storage_encrypted     = true

  multi_az               = var.db_multi_az
  db_subnet_group_name   = module.vpc.database_subnet_group_name
  vpc_security_group_ids = [aws_security_group.postgres.id]
  parameter_group_name   = aws_db_parameter_group.postgres.name
  publicly_accessible    = false

  backup_retention_period      = 7
  performance_insights_enabled = true
  deletion_protection          = var.deletion_protection
  skip_final_snapshot          = !var.deletion_protection
  final_snapshot_identifier    = var.deletion_protection ? "${local.name}-final" : null
}

# ---------------------------------------------------------------------------
# Redis-compatible cache (Valkey) for distributed rate limiting
# ---------------------------------------------------------------------------
resource "aws_security_group" "cache" {
  name_prefix = "${local.name}-cache-"
  description = "Valkey reachable only from EKS nodes"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "cache_from_nodes" {
  security_group_id            = aws_security_group.cache.id
  referenced_security_group_id = module.eks.node_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 6379
  to_port                      = 6379
}

resource "aws_elasticache_subnet_group" "cache" {
  name       = local.name
  subnet_ids = module.vpc.private_subnets
}

resource "aws_elasticache_replication_group" "cache" {
  replication_group_id       = local.name
  description                = "Herald rate-limit buckets"
  engine                     = "valkey"
  engine_version             = "8.0"
  node_type                  = var.cache_node_type
  num_cache_clusters         = 2
  automatic_failover_enabled = true
  multi_az_enabled           = true
  transit_encryption_enabled = true
  at_rest_encryption_enabled = true
  subnet_group_name          = aws_elasticache_subnet_group.cache.name
  security_group_ids         = [aws_security_group.cache.id]
}

# ---------------------------------------------------------------------------
# SQS: delivery work queue + redrive DLQ for poison messages
# ---------------------------------------------------------------------------
resource "aws_sqs_queue" "deliveries_dlq" {
  name                      = "${local.name}-deliveries-dlq"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "deliveries" {
  name                       = "${local.name}-deliveries"
  visibility_timeout_seconds = 60
  receive_wait_time_seconds  = 20
  message_retention_seconds  = 345600
  sqs_managed_sse_enabled    = true

  # Postgres remains the source of truth; this only catches messages that repeatedly crash a consumer.
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.deliveries_dlq.arn
    maxReceiveCount     = 5
  })
}
