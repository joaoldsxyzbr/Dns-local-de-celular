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
