# Arquitectura propuesta para V2

Móvil -> caché local de reputación -> CallScreeningService

Backend:
- GET /v1/reputation/{phone}
- POST /v1/reports
- GET /v1/stats
- autenticación anónima por instalación
- rate limiting
- normalización E.164
- sistema de reputación ponderada

Regla sugerida:
Riesgo 0-29: permitir
Riesgo 30-59: mostrar aviso
Riesgo 60-79: silenciar
Riesgo 80-100: bloquear

El prefijo 400 puede ser una señal de "comercial", pero no debe convertirse por sí solo en una prueba de fraude.
