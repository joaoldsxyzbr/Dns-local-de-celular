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
- Não existe serviço `VpnService` ligado à interface, nem DNS externo encaminhado: nenhum tráfego real foi filtrado. Um cliente UDP upstream separado foi adicionado, mas **ainda não está conectado à VPN**. Os sockets só podem enviar consultas após um callback obrigatório de proteção; falhas retornam nulo, sem resolver secundário implícito. O teste usa apenas um servidor UDP de loopback, verifica ID da transação, proteção obrigatória e comportamento diante de falhas.
- Não há medições de consumo de bateria; o Android SDK não está instalado no ambiente disponível. Build Android `./gradlew :app:assembleDebug` ainda não foi executado.
- Manter o CI para a validação final, conforme a decisão do projeto.

## Integração Android experimental (a validar)

O serviço nativo `LocalDnsVpnService` agora declara a permissão `BIND_VPN_SERVICE`, solicita autorização pela tela inicial, configura um endereço DNS virtual `198.18.0.1/32` com rota exclusiva ao destino e usa `VpnService.protect()` nos sockets de upstream. O app exige **IPv4 literal informado pelo usuário** e comunica que o transporte UDP/53 não é criptografado. A tela de status diferencia serviço DNS sem bloqueio de filtro HaGeZi. Serviço em primeiro plano usa o tipo `specialUse`, sujeito a revisão de requisitos caso o aplicativo seja distribuído via Play Store.

A execução ainda está **pendente de Android SDK, build real, dispositivo e verificação de roteamento de rede**. Não há garantia de funcionamento com DNS Privado, IPv6, TCP/53, outra VPN, sempre ativa ou bloqueio de tráfego fora da VPN. Não habilitar always-on neste protótipo. O cliente trabalha por eventos de leitura bloqueante, com até duas consultas simultâneas e fila limitada a 48; excesso de trabalho recebe SERVFAIL, não acumula tarefas sem limite.

Teste de fluxo de consultas (`tools/DnsTunnelProcessorSmoke.kt`): geração de resposta de sucesso, fallback explícito a SERVFAIL, resposta com ID incorreto e pacotes que não devem alcançar o upstream. Executar `sh tools/test-dns-core.sh` antes de afirmar aprovação do teste.

## Correção do modo de leitura e autorização

A API Android documenta que `VpnService.Builder.establish()` entrega a interface TUN em modo **não bloqueante** por padrão. Como este serviço usa `FileInputStream.read()` e uma thread dedicada, a configuração agora chama **`setBlocking(true)`** para impedir uma leitura em loop quando não existem consultas. Fonte: https://developer.android.com/reference/android/net/VpnService.Builder#setBlocking(boolean)

A `MainActivity` também retém o IP primário **já validado** enquanto o sistema pede consentimento, impedindo que mudanças no campo de edição alterem o destino depois da validação. O primeiro teste de comportamento (VPN/TUN real) permanece pendente de compilação Android e dispositivo; isto não prova economia de bateria ou compatibilidade.

## Continuidade IPv6 na VPN DNS-only

A documentação Android de `VpnService.Builder.allowFamily` especifica que famílias de endereços não configuradas ficam **bloqueadas** por padrão. Como a configuração experimental intercepta exclusivamente IPv4, adicionamos `.allowFamily(OsConstants.AF_INET6)` para permitir que conexões IPv6 **sigam pela rede subjacente**, sem capturá-las. Isso **não é bloqueio DNS IPv6** nem assegura cobertura de consultas de aplicativos com DoH/DoT. Requer teste físico com IPv6 e com a configuração "bloquear conexões sem VPN" do Android. Fonte: https://developer.android.com/reference/android/net/VpnService.Builder#allowFamily(int).

## Validação da correspondência da resposta upstream

O validador de resposta DNS agora exige transação, opcode e **pergunta completa** correspondentes à consulta enviada: QNAME (sem diferenciar maiúsculas), QTYPE e QCLASS. Respostas com o mesmo ID e domínio diferente não são aceitas e podem levar a fallback/resposta SERVFAIL; perguntas comprimidas ou malformadas são rejeitadas nessa verificação. O teste `responseIntegritySmoke()` cobre respostas adulteradas e EDNS(0), e o teste do servidor UDP simulado envia uma resposta de nome diferente antes da válida. **Só foi executado o teste Kotlin/JVM isolado do novo validador** neste incremento; integração Android e regressão completa ainda requerem ambiente de build.

## DNS de reserva e códigos de erro

O fallback opcional passa a considerar respostas **SERVFAIL (RCODE 2)** e **REFUSED (RCODE 5)** do primário como falhas elegíveis para tentar o DNS secundário. **NXDOMAIN (RCODE 3)** não gera fallback, pois pode indicar um nome inexistente ou bloqueado; o primário continua sendo o único consultado quando a chave de reserva está desativada. Testes unitários com DNS simulado cobrem essas situações. O transporte e a VPN ainda não foram validados no Android.
