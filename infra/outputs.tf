output "fila_comandos_url" {
  description = "URL da fila de comandos consumida pelo Billing."
  value       = aws_sqs_queue.billing_comandos.url
}

output "fila_comandos_arn" {
  description = "ARN da fila de comandos consumida pelo Billing."
  value       = aws_sqs_queue.billing_comandos.arn
}

output "dlq_comandos_url" {
  description = "URL da DLQ da fila de comandos."
  value       = aws_sqs_queue.billing_comandos_dlq.url
}

output "pod_role_arn" {
  description = "ARN da role associada ao service account billing-service."
  value       = aws_iam_role.pod.arn
}

output "db_endpoint" {
  description = "Endereco do RDS do Billing, publicado tambem no SSM."
  value       = aws_db_instance.billing.endpoint
}

output "db_security_group_id" {
  description = "Security group do RDS do Billing."
  value       = aws_security_group.rds.id
}
