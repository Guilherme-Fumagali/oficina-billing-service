resource "aws_ssm_parameter" "db_endpoint" {
  name        = "${local.prefixo_ssm}/db-endpoint"
  description = "Endereco host:porta do RDS do Billing"
  type        = "String"
  value       = aws_db_instance.billing.endpoint
}

resource "aws_ssm_parameter" "db_name" {
  name  = "${local.prefixo_ssm}/db-name"
  type  = "String"
  value = var.db_name
}

resource "aws_ssm_parameter" "db_username" {
  name  = "${local.prefixo_ssm}/db-username"
  type  = "String"
  value = var.db_username
}

resource "aws_ssm_parameter" "db_password" {
  name  = "${local.prefixo_ssm}/db-password"
  type  = "SecureString"
  value = random_password.db.result
}

resource "aws_ssm_parameter" "mp_access_token" {
  name        = "${local.prefixo_ssm}/mp-access-token"
  description = "Access token do vendedor de teste do Mercado Pago"
  type        = "SecureString"
  value       = var.mp_access_token
}

resource "aws_ssm_parameter" "mp_webhook_secret" {
  name        = "${local.prefixo_ssm}/mp-webhook-secret"
  description = "Assinatura secreta dos webhooks do Mercado Pago"
  type        = "SecureString"
  value       = var.mp_webhook_secret
}
