# Base experimental para DNS TCP dentro do túnel

O novo `DnsTcpPacketCodec.kt` lê segmentos IPv4/TCP destinados à porta 53 do DNS virtual, valida cabeçalho IPv4, integridade TCP com pseudo-cabeçalho, número de sequência e ACK, e pode codificar uma resposta sintética com checksums corretos. O método `singleDnsQuery` reconhece uma única mensagem DNS completa, com prefixo de comprimento (dois bytes).

**Evolução em 28/10/2026:** o codec e a máquina de estados foram integrados experimentalmente ao TUN. O fallback TCP **upstream** após `TC=1` permanece separado. Falta validação em aparelho, sobretudo sob perdas e DNS Privado.

## Verificação

Executar sob demanda: `sh tools/test-tcp-packet-core.sh` com JDK e `kotlinc`. O teste JVM sintético cobre IP/porta, checksums, cabeçalhos malformados, prefixo DNS incompleto e limite de tamanho 1500 bytes. O teste não consulta a internet nem inicia VPN. Android SDK, APK e dispositivo permanecem pendentes.

## Inicialização da VPN sem polling

Na abertura explícita de uma sessão, a `LocalDnsVpnService` agora lê/valida a lista HaGeZi uma única vez na thread `dns-list-session-loader`, evitando bloquear a thread principal do Android enquanto faz parsing. A VPN só é estabelecida no callback da thread principal após a leitura, com a geração da sessão conferida. Ao parar, reiniciar ou revogar, a geração é invalidada e a thread é interrompida; uma leitura antiga não pode reativar a VPN.

Isso **não cria polling**, consultas DNS de verificação, timers, atualizações periódicas, nova dependência nem novos logs. Enquanto a lista está sendo carregada, o status permanece `STARTING`. Se a lista ainda não existe ou está inválida, a VPN pode iniciar sem filtro e deve mostrar esse estado, como antes.

O script `python3 tools/test-vpn-startup-structure.py` verifica estas invariantes **somente por análise estática**; não compila Android nem comprova cancelamento de threads no dispositivo. Para testes reais, seguir `docs/android-device-validation.md`.

## Comprimento de mensagens DNS entre segmentos

O complemento `DnsTcpMessageFramer` acumula, sob limite estrito de 1.474 bytes, pedaços **já ordenados** de uma única consulta DNS/TCP. Rejeita prefixos fora do limite, bytes excedentes e respostas apresentadas como consultas. Também divide uma resposta de até 1.472 bytes em pedaços TCP de até 1.460 bytes, sem polling nem tarefas em segundo plano. Teste JVM independente: `sh tools/test-tcp-framing.sh`.

**Histórico:** o framer foi criado isoladamente; agora está conectado à máquina de estados TCP. Segmentos fora de ordem provocam ACK do próximo número esperado, sem buffer de reordenação. O comportamento em redes reais ainda não foi testado.

## Correção no limite de consultas TCP reagrupadas (28/10/2026)

A leitura `singleDnsQuery` antes limitava o pacote prefixado a 1.460 bytes (1.458 bytes úteis), o que rejeitava indevidamente uma consulta DNS válida de 1.472 bytes após remontagem por `DnsTcpMessageFramer`. O limite agora aceita até **1.474 bytes de framing** (prefixo de dois bytes + 1.472 bytes DNS), mantendo a rejeição de 1.473 bytes DNS. O teste de regressão foi reproduzido com `kotlinc`/JVM (falhou antes, passou após correção) e adicionado aos testes de codec e framing; não foi compilado em Android.

## Sessão TCP por eventos — componente isolado (28/10/2026)

O novo `DnsTcpSession` é uma máquina de estados **para uma única conexão e uma única consulta DNS/TCP**. Processa pacotes recebidos por chamada explícita a `accept`, sem `polling`, timers, sondagens, loops de espera ou trabalho periódico. Implementa SYN/SYN-ACK/ACK, sequência e confirmação esperadas (incluindo aritmética de 32 bits), leitura ordenada de fragmentos via `DnsTcpMessageFramer`, ACK de segmentos fora de ordem, resposta limitada ao MTU, FIN e descarte de payloads malformados. A retransmissão da resposta em memória só ocorre quando chega novamente o último segmento da consulta, com sequência e ACK correspondentes; **não existe retransmissão programada por tempo**.

O teste `sh tools/test-tcp-session.sh` cobre o handshake, SYN repetido, consulta dividida em segmentos, chegada fora de ordem, resposta, retransmissão sem nova resolução DNS, ACK errado, fechamento, prefixo inválido, falha do resolvedor com SERVFAIL e volta do contador de sequência (32 bits). Parte desses cenários passou em ensaio Kotlin/JVM com interfaces equivalentes às classes do repositório; o script versionado deve ser executado no checkout completo para assegurar a integração.

**Histórico:** a tabela agora é limitada a 64 sessões com ISNs de `SecureRandom`, negociação MSS e fechamento FIN com confirmação do cliente. Ainda faltam ensaios reais com perda de pacotes e validação de Android antes de considerar o TCP pronto para uso.

## Ativação experimental de DNS/TCP no TUN — 28/10/2026

O leitor TUN encaminha os pacotes TCP candidatos para **um worker TCP dedicado** e fila de até 48 tarefas. Os pacotes UDP continuam nos dois workers existentes. A geração de sessão impede escrita depois de parar/revogar a VPN; os recursos TCP são fechados pelo mesmo ciclo de vida do serviço.

O `DnsTcpTunnelProcessor` mantém no máximo **64 conexões simultâneas**, indexadas por IP de origem, porta de origem e IP virtual de destino. Apenas SYN novo cria conexão. O descarte de sessões antigas ocorre na chegada de outro pacote (LRU e limite de ociosidade de 60 segundos); **não existe varredura periódica, polling, timer ou sondagem**. ISNs são aleatórios pelo `SecureRandom`.

O SYN-ACK anuncia MSS 1460; o MSS fornecido pelo cliente é extraído das opções TCP e respeitado para segmentar respostas. Sem anúncio, aplica-se o padrão conservador de 536 bytes. O protocolo atende **uma consulta DNS por conexão**, montada de segmentos TCP recebidos na ordem; dados fora da ordem geram ACK para retransmissão. O FIN do servidor não remove prematuramente a sessão: ela aguarda e confirma o FIN do cliente.

Verificações versionadas: `sh tools/test-tcp-tunnel.sh`, `sh tools/test-tcp-session.sh`, `sh tools/test-tcp-framing.sh`, `sh tools/test-tcp-packet-core.sh` e `python3 tools/check-android-structure.py`. Há dados sintéticos para tabela limitada e fluxo TCP/TUN; **faltam execução integral das suítes no checkout Android, compilação APK, instalação, teste de perdas reais, DNS Privado e medição de bateria/RAM/CPU**. O CI será acionado apenas na última etapa do projeto.

## Segurança das retransmissões (28/10/2026)

O servidor verifica que uma retransmissão do último segmento de consulta tem o mesmo número de sequência, confirmação e **conteúdo de payload** antes de retornar respostas já armazenadas. Bytes adulterados ou ACK incorreto não podem fazer a sessão reapresentar uma resposta em cache. A regressão está em `tools/DnsTcpSessionSmoke.kt`. A proteção não substitui retransmissões TCP comandadas por timeout em redes com perda: **não existem temporizadores ou polling** neste protótipo e a resiliência completa a perda de pacotes continua pendente de validação em aparelho.
