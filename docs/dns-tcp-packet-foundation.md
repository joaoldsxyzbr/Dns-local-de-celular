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

## Correção no limite de consultas TCP reagrupadas (28/10/2026)

A leitura `singleDnsQuery` antes limitava o pacote prefixado a 1.460 bytes (1.458 bytes úteis), o que rejeitava indevidamente uma consulta DNS válida de 1.472 bytes após remontagem por `DnsTcpMessageFramer`. O limite agora aceita até **1.474 bytes de framing** (prefixo de dois bytes + 1.472 bytes DNS), mantendo a rejeição de 1.473 bytes DNS. O teste de regressão foi reproduzido com `kotlinc`/JVM (falhou antes, passou após correção) e adicionado aos testes de codec e framing; não foi compilado em Android.

## Sessão TCP por eventos — componente isolado (28/10/2026)

O novo `DnsTcpSession` é uma máquina de estados **para uma única conexão e uma única consulta DNS/TCP**. Processa pacotes recebidos por chamada explícita a `accept`, sem `polling`, timers, sondagens, loops de espera ou trabalho periódico. Implementa SYN/SYN-ACK/ACK, sequência e confirmação esperadas (incluindo aritmética de 32 bits), leitura ordenada de fragmentos via `DnsTcpMessageFramer`, ACK de segmentos fora de ordem, resposta limitada ao MTU, FIN e descarte de payloads malformados. A retransmissão da resposta em memória só ocorre quando chega novamente o último segmento da consulta, com sequência e ACK correspondentes; **não existe retransmissão programada por tempo**.

O teste `sh tools/test-tcp-session.sh` cobre o handshake, SYN repetido, consulta dividida em segmentos, chegada fora de ordem, resposta, retransmissão sem nova resolução DNS, ACK errado, fechamento, prefixo inválido, falha do resolvedor com SERVFAIL e volta do contador de sequência (32 bits). Parte desses cenários passou em ensaio Kotlin/JVM com interfaces equivalentes às classes do repositório; o script versionado deve ser executado no checkout completo para assegurar a integração.

**Não integrar diretamente ao VpnService ainda:** faltam a tabela de conexões com limite explícito e tratamento de colisões, seleção de sequência inicial com entropia adequada, negociação MSS/janela e fluxos de congestionamento, retransmissões/ACKs de dados sob perda real, FIN/RST em todas as condições, validação de tráfego real e testes Android. Não anunciar TCP DNS do celular como pronto. O código não altera a rota VPN nem os workers ativos.
