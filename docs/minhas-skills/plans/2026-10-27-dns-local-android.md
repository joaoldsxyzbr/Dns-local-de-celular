# Plano de implementação — DNS Local (Android)

**Data:** 2026-10-27  
**Estado:** planejamento aprovado para registro; implementação do APK ainda não iniciada.  
**Fonte da verdade:** este repositório.  
**Escopo:** aplicativo Android que filtra DNS localmente, sem servidor DNS próprio hospedado pelo projeto.

## 1. Decisões confirmadas na conversa

- **Nome do app:** DNS Local.
- **Tecnologia:** aplicativo **Android nativo em Kotlin** (não Java).
- **Prioridade máxima:** ser leve, estável e usar o mínimo possível de **bateria, RAM e CPU**. Não presumir metas numéricas antes de medir em aparelhos reais.
- **DNS configurável:** DNS primário e **secundário/reserva opcional**, com controle explícito para habilitar/desabilitar completamente o secundário. Desabilitado significa não criar consultas, sondagens ou fallback para ele.
- **Bloqueio de anúncios/rastreadores:** listas pré-definidas. **Padrão confirmado nesta conversa: HaGeZi Multi PRO Mini**. Outras listas podem ser oferecidas como opções, sem habilitá-las automaticamente.
- **Visual:** preservar a proposta de interface previamente aprovada; **a arte/telas originais não estão armazenadas neste repositório** e precisam ser recuperadas antes de alegar fidelidade visual.
- **Assinatura:** “DNS by” + nick do autor; **texto literal final do nick não está confirmado**.
- O usuário pediu **planejamento antes de iniciar a construção do APK**, não desenvolvimento ou publicação nesta etapa.

**Não foram decididos como requisitos pelo usuário:** versão mínima do Android, valores dos servidores DNS padrão, protocolo do resolvedor externo (DNS tradicional, DoT ou DoH), política de atualização, métricas de performance, integração com Play Store ou modelo de licenciamento do app. Recomendações técnicas abaixo são **propostas de implementação**, não decisões históricas.

## 2. O que o produto faz e o que não promete

### Fluxo esperado

1. Instalar e abrir o app; ver status **desligado/ligando/ativo/erro** e instrução clara de que o Android solicitará autorização de VPN.
2. Selecionar/configurar servidor DNS primário; ativar/desativar e configurar o secundário/reserva.
3. Iniciar a proteção. A consulta DNS do dispositivo que passar pelo serviço é confrontada com o conjunto local de domínios bloqueados; domínio liberado é resolvido pelo DNS configurado.
4. Atualizar e selecionar listas nas configurações. Exibir lista ativa, data de atualização e falhas sem interromper silenciosamente a navegação.
5. Parar a proteção e liberar o serviço pelo próprio aplicativo.

### Limites a comunicar

- **Sem root:** a arquitetura candidata é a API Android `VpnService` com VPN **local**, usada somente para encaminhar/filtrar consultas DNS. **Não é VPN de anonimato** e não cria um servidor remoto do projeto.
- O Android autoriza uma VPN por usuário/perfil de cada vez; o aplicativo não deve prometer funcionar simultaneamente com outra VPN.
- DNS privado (DoT), DNS-over-HTTPS próprio de apps/navegadores, servidores DNS incorporados, conexões por IP e conteúdo entregue pelo mesmo domínio podem escapar do bloqueio por DNS. **Não prometer 100% de bloqueio**, inclusive para anúncios em YouTube e similares.
- Há notificação de serviço ativo/ícone de VPN, conforme requisitos do sistema. Não solicitar permissões excessivas.
- Consultas destinadas ao DNS externo ainda necessitam de rede. O bloqueio é local, não o resolvedor autoritativo da internet.

## 3. Arquitetura técnica proposta (validar no primeiro protótipo)

**Stack inicial sugerida:** Kotlin + Gradle/Android Gradle Plugin em versões estáveis verificadas na implementação; APIs oficiais do Android. Preferir componentes nativos e minimizar dependências. UI nativa conforme o design recuperado; não impor Jetpack Compose ou XML sem avaliar a maquete aprovada e custos.

**Componentes e caminhos de código planejados** (a criar; nenhum existe ainda):

| Caminho proposto | Responsabilidade |
|---|---|
| `app/src/main/AndroidManifest.xml` | Declarar atividade, `VpnService`, permissões estritamente necessárias e serviço em primeiro plano compatível com o SDK-alvo. |
| `app/src/main/java/.../ui/` | Tela inicial, controle ligar/desligar, primário/secundário, seleção de lista, estados e mensagens. |
| `app/src/main/java/.../vpn/LocalDnsVpnService.kt` | Ciclo de vida da VPN local e da interface TUN; permissão, foreground, revogação e limpeza. |
| `app/src/main/java/.../dns/` | Leitura/escrita de pacotes IP/UDP/TCP DNS, validação, consulta, resposta, timeout, cache TTL limitado e política de bloqueio. |
| `app/src/main/java/.../resolver/` | Transporte protegido fora da VPN, endpoint primário, fallback opcional ao secundário e gestão de rede. |
| `app/src/main/java/.../blocklist/` | Download, validação, parser, deduplicação, armazenamento compacto, atualização atômica e busca por domínio/sufixo. |
| `app/src/main/java/.../settings/` | Persistência local de endpoints, chave do secundário, lista escolhida e preferências. |
| `app/src/test/` e `app/src/androidTest/` | Testes unitários de DNS/listas e testes Android com permissão, VPN e rede real. |
| `README.md` | Como instalar, configurar, verificar o serviço, limitações e estado do projeto. |

**Prova de viabilidade obrigatória antes de investir nas telas completas:**
- Criar VPN local com `VpnService.prepare()`, TUN e rotas restritas aos endereços DNS virtuais, evitando capturar o tráfego geral; verificar isso no dispositivo com Wi-Fi e rede móvel.
- Encaminhar consultas por sockets que utilizem `VpnService.protect()` (evitar loop VPN→VPN), respeitando troca de rede e IPv4/IPv6.
- Manipular requisições e respostas DNS sem corromper identificadores, tamanhos, timeouts, flags e códigos de resposta. Tratar UDP/53 e TCP/53 quando aplicável; pacotes fragmentados, EDNS(0), respostas grandes e compressão exigem estratégia/testes explícitos.
- Validar casos Android com DNS Privado ativado/desativado e registrar o que realmente pode ser filtrado. Se o escopo de roteamento DNS apenas não for fiável nas versões-alvo, **reavaliar a arquitetura antes de desenvolver o resto**, sem implementar uma VPN de tráfego completo disfarçadamente.

**Escolhas recomendadas sujeitas a validação:** pipeline orientado a eventos/suspenso quando ocioso; cache pequeno com TTL e limites; uma estrutura de busca compacta/imutável para lista ativa; sem varredura periódica constante; persistência simples, sem banco se não necessário; não manter histórico permanente de domínios consultados; transportes criptografados DoT/DoH são decisão de escopo adicional, não pressupor que DNS UDP/53 seja criptografado.

### DNS primário e secundário

- Primário: o único servidor consultado quando secundário estiver desligado.
- Chave secundário: **desligado** ⇒ salvar preferência e nunca enviar solicitações ao servidor secundário. **Ligado** ⇒ usar secundário somente diante de falha/timeout do primário (não round-robin em paralelo).
- Não falhar para um resolvedor oculto definido pelo aplicativo; comunicar claramente o erro quando nenhum servidor configurado funcionar.
- Alterações de endpoint/estado devem ter efeito controlado, evitar vazamento de consultas pendentes e persistir após reabrir o app.
- Validar entrada, endereços e configuração de IPv4/IPv6 e impedir hosts/URLs incorretos. O formato exato de entrada depende do protocolo que for escolhido.

## 4. Listas HaGeZi

**Padrão confirmado:** **HaGeZi Multi PRO Mini**, variante otimizada em tamanho para hardware com RAM limitada.

- **Fonte oficial:** https://github.com/hagezi/dns-blocklists
- **Referência da variante:** https://github.com/hagezi/dns-blocklists/blob/main/README.md#promini
- **Candidato a formato de domínios (avaliar parser e regras para subdomínios):**
  https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/wildcard/pro.mini-onlydomains.txt
- A contagem de entradas varia entre versões; **não** fixar um número como requisito.
- Política para MVP: uma lista principal selecionada de cada vez. Não juntar Pro, Pro Mini, Pro++ ou Ultimate, que têm sobreposição e aumentam consumo; outras opções pré-definidas podem incluir HaGeZi Light e Pro++ Mini com explicação do risco de falso positivo. A disponibilidade de opções deve ser testada contra as URLs oficiais.
- Fluxo: obter via HTTPS, impor limites de tamanho e tempo, verificar resposta/formato, normalizar nomes em ASCII/IDNA com regras explícitas, rejeitar linhas inválidas, deduplicar, compilar índice, e **trocar atomicamente** a lista anterior só após validação; em falha, manter a versão íntegra anterior.
- Usar dados da lista persistidos localmente; não baixar a cada consulta. Considerar refresh pouco frequente e preferencialmente com condições favoráveis de conectividade/energia; atualização manual disponível. Se não houver lista válida na primeira execução, informar “aguardando lista” e não afirmar que o bloqueio está ativo.
- Permitir desativar bloqueio sem desligar a resolução DNS, para diagnóstico. Contadores opcionais somente em memória, sem histórico identificável.
- **Licença:** as listas oficiais indicam GPL-3.0; verificar obrigações e atribuição antes de embutir snapshots/distribuir dados no APK. Não assumir que simplesmente copiar lista para dentro de um app fechado é juridicamente livre. Preferir obtenção da fonte oficial com crédito, respeitando licença e disponibilidade.
- Limite funcional: lista DNS não inspeciona HTTPS nem garante remoção de todos os anúncios.

## 5. Eficiência e privacidade

- Sem servidor próprio, conta, telemetria, trackers, anúncios no app ou coleta de histórico por padrão.
- O serviço trabalha por demanda; não fazer polling em segundo plano para testar DNS secundário, nem atualizações em intervalos curtos.
- Avaliar `JobScheduler`/`WorkManager` **somente se** atualizações periódicas forem necessárias; usar mecanismo do SO, não timer permanente.
- Evitar carregar várias listas redundantes simultaneamente; índice compacto com troca atômica e cache com teto mensurável.
- Falhas de conexão devem ter timeout finito e recuperação previsível, sem loop que acorde a CPU continuamente.
- Medir memória (PSS/heap), CPU, wakelocks, tráfego e consumo estimado de bateria **com tela apagada e em uso**, comparando com proteção desligada, em Android real. Não prometer percentuais sem evidências.
- Avisar sobre coexistência com outras VPNs; revogação e modo avião não podem deixar falso indicador de proteção ativa.
- Proteger armazenamento e rede: TLS com verificação normal de certificados, sem segredos hardcoded e sem logs de domínios em produção.

## 6. Telas e UX — escopo inicial

1. **Principal:** nome DNS Local, status real, botão Iniciar/Parar, status da lista e aviso de permissão de VPN.
2. **DNS:** campos de primário e secundário, chave **Usar DNS secundário (reserva)**, validação e indicação do servidor efetivamente em uso.
3. **Bloqueio:** seletor de listas predefinidas (padrão Pro Mini), ativar/desativar bloqueio, atualização e estado de versão.
4. **Sobre/ajuda:** assinatura `DNS by <nick a confirmar>`, breve explicação de limites do bloqueio, licença/créditos e instruções de solução de problemas.

**Regra de design:** manter o visual previamente aprovado assim que sua referência estiver disponível; se não estiver, produzir telas simples e funcionais e marcar fidelidade visual como pendente, sem afirmar reprodução exata.

## 7. Etapas executáveis, entregáveis e validação

### Etapa 0 — Inicialização e critérios de plataforma
- Criar projeto Kotlin nativo, wrapper Gradle e manifest; escolher `minSdk`, `targetSdk` e versões estáveis com base nos aparelhos-alvo.
- Definir protocolo/entrada de DNS, modelo de estados, regras de falha, sem adicionar funcionalidades não combinadas.
- **Verificação proposta:** `./gradlew :app:assembleDebug`; abrir APK de depuração e validar instalação/abertura em emulador e Android real.

### Etapa 1 — Protótipo vertical de DNS local
- Implementar somente consentimento VPN, serviço, roteamento DNS restrito, leitura e resposta de consultas e transporte protegido para primário.
- **Verificação proposta:** teste controlado de consulta A/AAAA, UDP/TCP, IPv4/IPv6, DNS Privado, Wi-Fi/dados móveis, desligamento/revogação, sem interceptar web geral. **Critério de parada:** se rotas não forem fiáveis, redesenhar antes de prosseguir.

### Etapa 2 — DNS secundário opcional e persistência
- Adicionar validação, estado salvo, chave on/off e fallback **apenas quando ativado**, com timeout e retorno ao primário em condições controladas.
- **Verificação proposta:** teste automatizado com resolvedor primário que falha; quando secundário desligado, zero requisições de fallback; quando ligado, fallback ocorre; reabrir app e confirmar preferências.

### Etapa 3 — Motor de listas e HaGeZi Multi PRO Mini
- Parser da fonte oficial, matching por domínio/subdomínio, atualização segura, cache local, opção de desligar filtro e status real.
- **Verificação proposta:** testes para domínio permitido/bloqueado, subdomínio, case/IDN, arquivo inválido, atualização interrompida, lista vazia e false positive recuperável. Medir custo de RAM real da Pro Mini.

### Etapa 4 — Interface definitiva e integração
- Montar as telas alinhadas com visual aprovado, estados reais, erros compreensíveis, acessibilidade básica e créditos.
- **Verificação proposta:** cenários manuais de primeira inicialização, consentimento negado, VPN substituída, troca de DNS/lista, modo avião, reinício e orientação/tamanho de fonte.

### Etapa 5 — Otimização, robustez e APK
- Medições e correções de bateria/RAM/CPU; revisão de privacidade/licença; validação final no(s) Android(s) alvo.
- **Verificações propostas:** `./gradlew test`, `./gradlew connectedAndroidTest` (quando disponível) e `./gradlew :app:assembleDebug`; testar o **APK real** no dispositivo. Se houver assinatura/distribuição, definir segurança de chaves e método em etapa separada.
- **Entrega:** APK testável e instruções de instalação. APK de lançamento somente após validação e autorização da distribuição, sem publicar automaticamente.

Os comandos acima são **planos de verificação**: nenhum build ou teste Android foi executado ao criar este documento.

## 8. Critérios de aceitação do MVP

- [ ] APK Kotlin nativo instala e abre nas versões Android testadas.
- [ ] Ativar exige consentimento da VPN quando aplicável, com indicação honesta de status e notificação em execução.
- [ ] Tráfego DNS coberto pela VPN passa por filtro local, mas tráfego comum não é redirecionado para servidor VPN remoto.
- [ ] Primário funciona; secundário pode ser habilitado, desabilitado e persistido; **desabilitado nunca é consultado**.
- [ ] HaGeZi Multi PRO Mini selecionada como padrão e aplicada sem downloads em cada consulta; atualização inválida mantém última lista válida.
- [ ] Bloqueio pode ser desligado sem impedir resolução normal do DNS; erros são exibidos.
- [ ] Ausência de telemetria/histórico de consultas persistente e ausência de operações em loop quando ocioso.
- [ ] Medições de bateria, RAM e CPU com aparelho real registradas; gargalos tratados sem inventar benchmark.
- [ ] Limitações de VPN/DoH/DoT claramente comunicadas; licença/créditos HaGeZi revistos.
- [ ] Identidade visual cotejada com referência original quando disponibilizada.

## 9. Pendências reais (não bloquear o protótipo técnico)

- **Confirmar posteriormente:** nick literal da assinatura; maquete/arte visual previamente aprovada.
- **Resolver tecnicamente na Etapa 0:** versões Android alvo, protocolo de DNS suportado na v1, formato e validação dos endpoints, valores iniciais do primário/secundário e comportamento na primeira execução.
- **Avaliar em aparelho:** compatibilidade prática com DNS Privado e limites do serviço; se necessário, ajustar escopo com transparência.
- **Revisar antes de distribuir:** licença GPL-3.0 e eventuais obrigações sobre as listas HaGeZi; conformidade do tipo de foreground service com Android/Google Play, se houver publicação.

## 10. Referências oficiais consultadas (2026-10-27)

- Android — VPN e consentimento/ciclo de vida: https://developer.android.com/develop/connectivity/vpn
- Android — `VpnService` e proteção de sockets: https://developer.android.com/reference/android/net/VpnService
- Android — `VpnService.Builder`: https://developer.android.com/reference/android/net/VpnService.Builder
- Android — foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- HaGeZi — listas e links oficiais: https://github.com/hagezi/dns-blocklists
- HaGeZi — FAQ, combinações e licença: https://github.com/hagezi/dns-blocklists/blob/main/FAQ.md

---
**Checkpoint de continuidade:** planejamento documentado; **nenhum código Android, build ou APK foi produzido nesta etapa**. Próxima ação autorizável: Etapa 0, seguida da prova de viabilidade da Etapa 1. Não tratar etapas futuras como concluídas sem testes e evidências.
