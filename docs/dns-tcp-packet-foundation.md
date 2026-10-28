# Base experimental para DNS TCP dentro do túnel

O novo `DnsTcpPacketCodec.kt` lê segmentos IPv4/TCP destinados à porta 53 do DNS virtual, valida cabeçalho IPv4, integridade TCP com pseudo-cabeçalho, número de sequência e ACK, e pode codificar uma resposta sintética com checksums corretos. O método `singleDnsQuery` reconhece uma única mensagem DNS completa, com prefixo de comprimento (dois bytes).

**Ainda não está ligado ao serviço VPN.** Não foram implementados handshake TCP, estado de conexões, retransmissões, remontagem de segmentos parciais, fechamento ou respostas TCP maiores que um segmento. Esses recursos são necessários antes de afirmar que DNS TCP originado no Android é atendido. O fallback TCP **upstream** após `TC=1` já é outra funcionalidade existente.

## Verificação

Executar sob demanda: `sh tools/test-tcp-packet-core.sh` com JDK e `kotlinc`. O teste JVM sintético cobre IP/porta, checksums, cabeçalhos malformados, prefixo DNS incompleto e limite de tamanho 1500 bytes. O teste não consulta a internet nem inicia VPN. Android SDK, APK e dispositivo permanecem pendentes.

## Inicialização da VPN sem polling

Na abertura explícita de uma sessão, a `LocalDnsVpnService` agora lê/valida a lista HaGeZi uma única vez na thread `dns-list-session-loader`, evitando bloquear a thread principal do Android enquanto faz parsing. A VPN só é estabelecida no callback da thread principal após a leitura, com a geração da sessão conferida. Ao parar, reiniciar ou revogar, a geração é invalidada e a thread é interrompida; uma leitura antiga não pode reativar a VPN.

Isso **não cria polling**, consultas DNS de verificação, timers, atualizações periódicas, nova dependência nem novos logs. Enquanto a lista está sendo carregada, o status permanece `STARTING`. Se a lista ainda não existe ou está inválida, a VPN pode iniciar sem filtro e deve mostrar esse estado, como antes.

O script `python3 tools/test-vpn-startup-structure.py` verifica estas invariantes **somente por análise estática**; não compila Android nem comprova cancelamento de threads no dispositivo. Para testes reais, seguir `docs/android-device-validation.md`.

## Comprimento de mensagens DNS entre segmentos

O complemento `DnsTcpMessageFramer` acumula, sob limite estrito de 1.474 bytes, pedaços **já ordenados** de uma única consulta DNS/TCP. Rejeita prefixos fora do limite, bytes excedentes e respostas apresentadas como consultas. Também divide uma resposta de até 1.472 bytes em pedaços TCP de até 1.460 bytes, sem polling nem tarefas em segundo plano. Teste JVM independente: `sh tools/test-tcp-framing.sh`.

**Falta** controle da sequência TCP, remontagem de pacotes fora de ordem, handshake, retransmissão e encerramento de sessão. Portanto este framer continua isolado e **não é ativação de DNS TCP no túnel**. O código em Android só será ligado após validações específicas.
