# DNS Local — critérios para fechar o desenvolvimento

**Situação em 28/10/2026:** código experimental em `main`; ainda não há prova de funcionamento no Android real. Não distribuir como aplicativo concluído nem afirmar economia de bateria sem medições.

## Validações automáticas do código

O último CI deve executar, sem tarefas agendadas:

- `python3 tools/check-android-structure.py`
- `python3 tools/test-vpn-startup-structure.py`
- `sh tools/test-blocklist-core.sh`
- `sh tools/test-filter-pipeline.sh`
- `sh tools/test-dns-core.sh`
- `sh tools/test-tcp-packet-core.sh`
- `sh tools/test-tcp-framing.sh`
- `sh tools/test-tcp-session.sh`
- `sh tools/test-tcp-tunnel.sh`
- `./gradlew --no-daemon :app:assembleDebug`

O APK **debug** resultante, se o build passar, é apenas para teste autorizado. Um CI aprovado atesta essas verificações no runner; **não substitui a instalação real, conexão de rede ou ensaios de energia**.

## Validações obrigatórias em Android físico antes de marcar o projeto como concluído

- [ ] Instalar e abrir o APK em aparelho Android compatível, aceitar/revogar a permissão oficial de VPN e confirmar status/notificação coerentes.
- [ ] Testar consultas DNS IPv4/UDP e DNS IPv4/TCP/53 em Wi-Fi e dados móveis, domínios permitidos/bloqueados (HaGeZi) e NXDOMAIN.
- [ ] Confirmar zero tráfego para DNS reserva quando desligado, e fallback somente quando habilitado.
- [ ] Testar consultas divididas entre segmentos TCP, MSS, FIN/RST, perda/retransmissão e troca de redes. Não pressupor funcionamento após os testes sintéticos.
- [ ] Verificar que navegação geral não passa pela VPN e que IPv6, DNS Privado/DoT e DoH possuem limitações claramente informadas.
- [ ] Medir bateria, CPU e RAM de modo comparável, com VPN ligada e desligada, inclusive tela apagada.
- [ ] Revisar uso/atribuição da HaGeZi (download manual da lista oficial), as exigências Android de serviço em primeiro plano e a interface visual em dispositivo.
- [ ] Confirmar assinatura final do autor, design aprovado e os elementos de tela ausentes antes de publicação.

Para registro por aparelho, seguir `docs/android-device-validation.md`. Não publicar logs com consultas privadas nem dados pessoais. Não executar tarefas em polling, downloads periódicos nem CI a cada commit durante o desenvolvimento.
