# Protótipo do DNS local — núcleo de pacotes (primeiro incremento da Etapa 1)

Este incremento **não ativa a VPN** e **não bloqueia anúncios**. Ele implementa, em Kotlin puro, operações que o serviço Android usará depois: analisar datagramas IPv4/UDP dirigidos a um endereço DNS virtual, construir resposta com cabeçalhos/checksums IP e UDP válidos, conferir o ID da resposta DNS e gerar SERVFAIL explícito.

## Verificações reproduzíveis

Com JDK e compilador Kotlin disponíveis (o compilador local 1.9.0 foi usado para um smoke test independente do Android):

```sh
sh tools/test-dns-core.sh
```

Resultado observado localmente: `OK: DNS packet codec smoke checks passed`. Os testes cobrem endereços IPv4 válidos/inválidos, empacotamento/desempacotamento, checksums independentes, respostas SERVFAIL, flags, limites de tamanho e rejeição de pacotes inválidos ou fragmentados.

## Restrições importantes

- Suporta apenas IPv4 + UDP/53, com MTU 1500. TCP DNS, IPv6, EDNS/truncamento, fragmentação e mudança de rede **ainda precisam ser tratados/testados**.
- Não existe serviço `VpnService` ligado à interface, nem DNS externo encaminhado: nenhum tráfego real foi filtrado.
- Não há medições de consumo de bateria; o Android SDK não está instalado no ambiente disponível. Build Android `./gradlew :app:assembleDebug` ainda não foi executado.
- Manter o CI para a validação final, conforme a decisão do projeto.
