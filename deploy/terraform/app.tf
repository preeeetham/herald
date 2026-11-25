# ---------------------------------------------------------------------------
# Container registries
# ---------------------------------------------------------------------------
resource "aws_ecr_repository" "service" {
  for_each             = toset(["api", "worker", "dashboard"])
  name                 = "herald-${each.key}"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "service" {
  for_each   = aws_ecr_repository.service
  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the 30 most recent images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 30 }
      action       = { type = "expire" }
    }]
  })
}

# ---------------------------------------------------------------------------
# Application secrets (synced into Kubernetes by External Secrets Operator)
# ---------------------------------------------------------------------------
resource "random_password" "admin_token" {
  length  = 48
  special = false
}

resource "random_bytes" "encryption_key" {
  length = 32
}

resource "aws_secretsmanager_secret" "app" {
  name_prefix             = "${local.name}/app-"
  description             = "Herald admin token and AES-256 key for endpoint signing secrets"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "app" {
  secret_id = aws_secretsmanager_secret.app.id
  secret_string = jsonencode({
    HERALD_ADMIN_TOKEN    = random_password.admin_token.result
    HERALD_ENCRYPTION_KEY = random_bytes.encryption_key.base64
  })
}

# ---------------------------------------------------------------------------
# Pod identity for the "herald" service account: least-privilege SQS access
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "workload_trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [module.eks.oidc_provider_arn]
    }
    condition {
      test     = "StringEquals"
      variable = "${module.eks.oidc_provider}:sub"
      values   = ["system:serviceaccount:herald:herald"]
    }
    condition {
      test     = "StringEquals"
      variable = "${module.eks.oidc_provider}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "workload" {
  statement {
    sid = "DeliveryQueue"
    actions = [
      "sqs:SendMessage",
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:ChangeMessageVisibility",
      "sqs:GetQueueUrl",
      "sqs:GetQueueAttributes",
    ]
    resources = [aws_sqs_queue.deliveries.arn]
  }
}

resource "aws_iam_role" "workload" {
  name_prefix        = "${local.name}-workload-"
  assume_role_policy = data.aws_iam_policy_document.workload_trust.json
}

resource "aws_iam_role_policy" "workload" {
  role   = aws_iam_role.workload.id
  policy = data.aws_iam_policy_document.workload.json
}
