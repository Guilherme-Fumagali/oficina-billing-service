#!/bin/bash
set -euo pipefail

criar_fila() {
  local nome="$1"
  local dlq_url dlq_arn atributos
  dlq_url=$(awslocal sqs create-queue --queue-name "${nome}-dlq" \
    --attributes MessageRetentionPeriod=1209600 --query QueueUrl --output text)
  dlq_arn=$(awslocal sqs get-queue-attributes --queue-url "$dlq_url" \
    --attribute-names QueueArn --query Attributes.QueueArn --output text)
  atributos=$(printf '{"VisibilityTimeout":"30","MessageRetentionPeriod":"345600","RedrivePolicy":"{\\"deadLetterTargetArn\\":\\"%s\\",\\"maxReceiveCount\\":\\"5\\"}"}' "$dlq_arn")
  awslocal sqs create-queue --queue-name "$nome" --attributes "$atributos" > /dev/null
  echo "fila ${nome} criada com DLQ ${nome}-dlq"
}

for fila in os-eventos billing-comandos execution-comandos; do
  criar_fila "oficina-local-${fila}"
done
