# HaGeZi Multi PRO Mini — bloqueio local experimental

O aplicativo consulta a lista **Multi PRO Mini** no formato **onlydomains** da fonte oficial, **somente quando o usuário toca em “Baixar / atualizar”**:

https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/wildcard/pro.mini-onlydomains.txt

O download é HTTPS com verificação padrão do certificado, sem redirecionamentos, timeout de conexão/leitura e limite de 16 MiB. O arquivo é salvo temporariamente, validado, e **substituído atomicamente** via `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`; falhas deixam o arquivo anterior. O conjunto de domínios deve ter ao menos 10.000 entradas válidas. O índice usa domínios ordenados e encontra subdomínios; não há sincronização periódica, coleta ou log de consultas.

Quando o filtro é habilitado e há lista válida, consultas DNS convencionais de classe IN por um nome que aparece no índice (ou subdomínio) recebem **NXDOMAIN** diretamente no túnel; **não são enviadas ao DNS primário nem secundário**. Consultas não bloqueadas continuam usando os resolvedores configurados. Se a lista não foi baixada, está inválida ou o controle de bloqueio foi desativado, o serviço encaminha as consultas sem filtrar.

A VPN utiliza uma cópia imutável da lista lida ao iniciar. **É necessário parar e reiniciar o DNS** para aplicar uma nova lista ou alteração no controle do filtro. A tela identifica lista carregada, atualização, falha e o estado da VPN com ou sem filtro. A notificação indica quando uma lista está carregada.

Os testes locais em Kotlin/JVM foram executados com sucesso:
- `sh tools/test-filter-pipeline.sh`: bloqueio DNS, subdomínios, AAAA, EDNS(0), ausência de consultas upstream quando bloqueado; persistência atômica, arquivo incompleto/inválido, limite de tamanho e preservação da versão íntegra.
- `sh tools/test-blocklist-core.sh`: matching e parser.
- `sh tools/test-dns-core.sh`: protocolo DNS e fallback.

**Limites:** o filtro cobre apenas as consultas DNS IPv4/UDP capturadas pela VPN experimental. Não há comprovação de funcionamento Android, nem interceptação de DNS Privado, DoH, IPv6 ou TCP. NXDOMAIN bloqueia nomes, não conteúdos dentro de domínios permitidos. Falta testar o APK em aparelho, medir RAM/bateria, fazer revisão de licença GPL-3.0 antes da redistribuição e avaliar tratamento de falsos positivos.

Créditos e fonte: [HaGeZi DNS Blocklists](https://github.com/hagezi/dns-blocklists). Este projeto não embute uma cópia da lista no APK; obtém os dados remotamente mediante ação do usuário.

## Otimização da normalização (Kotlin/JVM)

Os domínios ASCII comuns passam pela validação de sintaxe sem chamar `java.net.IDN.toASCII` a cada pesquisa. Nomes com Unicode ou prefixo `xn--` continuam usando a conversão/validação IDN. O matching, deduplicação e índice imutável permanecem os mesmos.

A regressão independente em Kotlin/JVM passou com ASCII, normalização de maiúsculas, Unicode/punycode, domínios inválidos, sufixos e um **índice sintético de 50.000 entradas**. Os testes não usaram nem redistribuíram dados da HaGeZi. Isso é uma otimização de caminho de código: não constitui medição de bateria, RAM ou CPU em Android real.

## Resiliência e cancelamento, sem polling

- O armazenamento rejeita imediatamente um `InputStream.read` que retorne **zero bytes sem EOF**, para não rodar um laço sem progresso. Uma falha de leitura preserva a última versão íntegra.
- O parser de até 250.000 domínios confere o sinal de interrupção durante o **processamento já em andamento** e encerra a leitura quando a VPN cancela a sessão. Isso não faz sondagens de rede nem adiciona timer.
- A tela interrompe a leitura usada para exibir o estado da lista ao ser destruída (por exemplo, ao fechar/girar o aplicativo). A VPN continua usando a própria cópia imutável da lista por sessão.
- Testes Kotlin/JVM de fluxos sintéticos passaram para leitura zerada, preservação do cache e cancelamento. A otimização ainda **não foi medida em Android**.

## Cancelamento da atualização manual (28/10/2026)

Ao destruir a tela (inclusive por rotação), a thread de **atualização manual** é interrompida, assim como a thread que consulta o estado da lista. O downloader HTTPS rejeita início ou persistência quando o cancelamento já foi solicitado. O armazenamento verifica a interrupção antes e depois de cada leitura e **antes da substituição atômica**, mantendo a última lista íntegra e limpando arquivos temporários. Uma chamada de rede já bloqueada pode levar até seu timeout configurado para retornar; não se promete cancelamento instantâneo de sockets. Sem polling, agendamento ou nova dependência.

A regressão JVM em `tools/HaGeZiListStoreSmoke.kt` cobre um fluxo interrompido após a primeira leitura, sem leituras adicionais e sem substituir o arquivo válido. A interrupção HTTPS em Android real continua pendente de ensaio.
