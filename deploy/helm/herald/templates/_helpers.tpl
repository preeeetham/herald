{{- define "herald.fullname" -}}
{{- printf "%s" .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "herald.labels" -}}
app.kubernetes.io/part-of: herald
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end -}}

{{- define "herald.selector" -}}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{- define "herald.image" -}}
{{ .root.Values.image.registry }}/herald-{{ .name }}:{{ .root.Values.image.tag | default .root.Chart.AppVersion }}
{{- end -}}

{{/* Environment shared by the API and the worker. */}}
{{- define "herald.commonEnv" -}}
- name: HERALD_DB_URL
  value: {{ .Values.config.database.url | quote }}
- name: HERALD_DB_USER
  value: {{ .Values.config.database.user | quote }}
- name: HERALD_ALLOW_PRIVATE_NETWORKS
  value: {{ .Values.config.allowPrivateNetworks | quote }}
- name: HERALD_TRACING_ENABLED
  value: {{ .Values.config.tracing.enabled | quote }}
- name: HERALD_OTLP_ENDPOINT
  value: {{ .Values.config.tracing.otlpEndpoint | quote }}
- name: HERALD_TRACE_SAMPLING
  value: {{ .Values.config.tracing.sampling | quote }}
- name: LOGGING_STRUCTURED_FORMAT_CONSOLE
  value: ecs
- name: JAVA_TOOL_OPTIONS
  value: "-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"
{{- with .Values.config.extraEnv }}
{{ toYaml . }}
{{- end }}
{{- end -}}

{{- define "herald.probes" -}}
startupProbe:
  httpGet: { path: /actuator/health/liveness, port: http }
  periodSeconds: 3
  failureThreshold: 40
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: http }
  periodSeconds: 10
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: http }
  periodSeconds: 5
{{- end -}}
