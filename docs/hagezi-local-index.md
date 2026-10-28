# HaGeZi Multi PRO Mini — índice local (em desenvolvimento)

A versão inicial contém somente o **parser e índice imutável de domínios**. Não inclui um snapshot da lista HaGeZi no APK, download automático nem integração com a VPN. Nenhum bloqueio é aplicado enquanto estes passos não estiverem implementados.

- Formato previsto: **uma linha de domínio por entrada**, arquivo `wildcard/pro.mini-onlydomains.txt` da fonte oficial: https://github.com/hagezi/dns-blocklists/blob/main/README.md#promini
- URL prevista para atualização via HTTPS, sujeita a verificação e licenciamento antes da integração: https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/wildcard/pro.mini-onlydomains.txt
- Indexação por array ordenado, com busca binária e comparação de sufixos por limites de rótulo. Sem loop de atualização ou registro de consultas; apenas um conjunto em memória.
- Parser rejeita domínios inválidos, linhas que não sejam `onlydomains`, lista vazia e entrada além dos limites configurados; remove duplicatas; normaliza IDN.
- Teste local independente de Android: `sh tools/test-blocklist-core.sh`. Foi executado com Kotlin 1.9/JDK 21 e passou em fixtures artificiais (inclusive subdomínios, IDN, comentários, duplicação e entradas inválidas).
- Ainda falta baixar a lista via HTTPS com limites de tamanho, validar recebimento, persistir versão íntegra, trocar atomicamente, definir resposta DNS de bloqueio, ligar/desligar filtro e medir memória em Android real.
- Conferir licenciamento da fonte antes de redistribuição. Não afirmar compatibilidade ou bloqueio antes de testes de ponta a ponta.
