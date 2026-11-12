data "aws_iam_policy_document" "confianca_pod" {
  statement {
    actions = ["sts:AssumeRole", "sts:TagSession"]

    principals {
      type        = "Service"
      identifiers = ["pods.eks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "pod" {
  name                 = "oficina-billing-pod-${var.ambiente}"
  description          = "Identidade do pod billing-service via EKS Pod Identity"
  assume_role_policy   = data.aws_iam_policy_document.confianca_pod.json
  permissions_boundary = "arn:aws:iam::${local.conta}:policy/oficina-pod-boundary"
}

data "aws_iam_policy_document" "pod" {
  statement {
    sid = "ConsumirComandos"
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:ChangeMessageVisibility",
      "sqs:GetQueueUrl",
      "sqs:GetQueueAttributes",
    ]
    resources = [aws_sqs_queue.billing_comandos.arn]
  }

  statement {
    sid       = "MonitorarDlq"
    actions   = ["sqs:GetQueueUrl", "sqs:GetQueueAttributes"]
    resources = [aws_sqs_queue.billing_comandos_dlq.arn]
  }

  statement {
    sid       = "PublicarEventosOs"
    actions   = ["sqs:SendMessage", "sqs:GetQueueUrl", "sqs:GetQueueAttributes"]
    resources = [local.fila_os_eventos]
  }
}

resource "aws_iam_role_policy" "pod" {
  name   = "oficina-billing-pod-${var.ambiente}-sqs"
  role   = aws_iam_role.pod.id
  policy = data.aws_iam_policy_document.pod.json
}

resource "aws_eks_pod_identity_association" "billing" {
  cluster_name    = data.aws_ssm_parameter.eks_cluster_name.value
  namespace       = "oficina"
  service_account = "billing-service"
  role_arn        = aws_iam_role.pod.arn
}
