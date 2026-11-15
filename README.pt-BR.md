# Loconautics × Immersive Portals: Aeronautics

[English](README.md) | **Português (Brasil)**

Mod de compatibilidade para NeoForge 1.21.1. Com ele, os trens do **Loconautics** atravessam portais do **Immersive Portals: Aeronautics** (IP Aero) inteiros:
- não descarrilam e nenhum vagão some;
- os engates continuam presos;
- você não perde o controle;
- as escolhas de desvio continuam coerentes.

Quem faz a travessia é o *straddle* do IP Aero, que leva a nave do trem de um mundo para o outro. Este mod desliga a travessia própria do Loconautics e conserta o que quebraria do outro lado.

> Add-on não oficial. Ele não tem ligação com os autores do Loconautics, do Immersive Portals: Aeronautics, do Sable nem do Create.

---

## O que ele faz

### Travessia
- **Um só sistema de travessia:** a travessia de portal do Loconautics (`TrainPortalCrossing`) é desligada, então o "gêmeo" e o corte com fade não aparecem mais. Sem isso, os dois mods brigam pelo trem.
- **O trem sobrevive à mudança:** enquanto o IP Aero move o vagão, ele fica marcado como "atravessando", e o Loconautics não apaga o trem quando a nave sai do mundo.
- **As rodas fantasmas ficam para trás:** elas são naves à parte, presas a cada vagão. São removidas logo antes da travessia, e o Loconautics monta rodas novas do outro lado, em vez de o vagão bater em rodas soltas.
- **As coordenadas internas são corrigidas:** o IP Aero desloca as coordenadas internas (o plot) da nave. O mod corrige tudo o que o Loconautics guarda nelas:
  - a posição dos truques;
  - os pontos de frente e de trás do vagão;
  - o parceiro de cada engate;
  - a posição dos controladores.

### De volta ao trilho
- **Re-encaixe no lugar:** cada vagão é assentado no trilho embaixo dele, sem recriar a nave. Era a recriação que fazia o trem sumir para quem olhava pelo portal.
- **A marcha continua igual:** velocidade, acelerador, sentido de marcha e parada em estação ficam como antes do portal.
- **Sem virar 180°:** o vagão mantém a orientação, inclusive vagões de um truque só.
- **Trilho que já atravessou é mantido:** quando o próprio trilho passa pelo portal (trilho de portal do Create), os trilhos "virtuais" do trem chegam do outro lado antes da nave. A posição deles e os lados de desvio que já escolheram são mantidos.

### Desvios
- **Desvio na saída do portal:** quando o trem chega, um desvio pode ficar entre os truques ou entre os vagões. O mod grava esse desvio na memória do trem com o lado em que os truques realmente estão. Assim os truques e vagões de trás seguem o mesmo lado, em vez de se separarem.
- **Escolhas velhas são apagadas:** escolhas de desvio feitas antes da entrega, que não batem mais com a posição real dos truques, são descartadas.

### Engates e controles
- **Os engates seguram:** vagões ligados por **Knuckle** continuam engatados. A referência do parceiro acompanha cada vagão, e vagões engatados continuam sem colidir entre si.
- **O controle manual continua:** o **Analog Controller** fica com o mesmo jogador, com o mesmo acelerador e as teclas seguradas. Se jogador e trem ficarem um instante em mundos diferentes, ele espera até 5 segundos antes de soltar. No cliente, as teclas não voltam para o jogador no meio da travessia, então você não sai andando.

### Correções que valem também longe do portal
- **Redstone Link dentro de naves:** os receptores de link do Loconautics (como o Bearing Axle) passam a calcular o alcance pela posição real da nave quando se registram de novo, ao carregar ou depois de uma travessia. Antes, um transmissor no chão podia parecer estar a milhões de blocos.
- **Virar sem ninguém no controle:** um vagão cujo Bearing Axle recebe comando de virar (switch de painel, Redstone Link ou redstone direto) conta como "dirigido", e o trem inteiro segue esse comando nos desvios. Antes, quem chegava primeiro ao desvio decidia sozinho e geralmente ia reto. Podia ser o vão do vagão ou um vagão sem eixo na frente.

## Requisitos

Testado com:

| Mod | Versão |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.265 |
| Loconautics | 0.1.3 BETA (a 0.1.2 BETA também funciona) |
| Immersive Portals: Aeronautics | 2.8.27 |
| Sable | 2.0.6 |
| Create | 6.0.10 |
| Create Aeronautics | 1.3.2 |

O Loconautics e o Immersive Portals: Aeronautics são **obrigatórios**. Outras versões podem funcionar, mas o mod usa partes internas dos dois (veja [Compatibilidade](#compatibilidade)).

## Instalação

1. Baixe o jar em [Releases](../../releases), ou compile você mesmo (veja abaixo).
2. Coloque o `loconautics_ip_compat-<versão>.jar` na pasta `mods/`, **no cliente e no servidor**: parte da correção roda em cada lado.
3. Abra o jogo. Não há nada para configurar.

## Uso

Monte o trem do Loconautics como sempre e passe por um portal do IP Aero, com o trilho continuando do outro lado.

Você pode dirigir:
- pelo Analog Controller;
- por controles externos (redstone, horários);
- por um switch de direção no próprio trem, como "piloto automático".

Funciona nos dois sentidos, quantas vezes quiser, com vagão sozinho ou composição engatada.

O mod registra no log o que faz, sempre com `[LocoIP]` na frente.

### Diagnóstico

O comando `/locoip debug` (nível de permissão 2) liga e desliga um rastreio detalhado de desvios:
- o registro de desvios do próprio Loconautics, que vem bloqueado nas versões publicadas;
- uma linha para cada decisão da memória de desvios;
- o estado de cada truque logo depois de ser assentado do outro lado do portal.

Vem desligado e não muda o comportamento. Desligue de novo quando terminar, porque ele enche o log rápido.

## Como funciona

Todos os ganchos são mixins; não há arquivo de configuração.

| Mixin | Alvo | O que faz |
| --- | --- | --- |
| `TrainPortalCrossingMixin` | `TrainPortalCrossing` do Loconautics | Cancela o `tick`; o `isCrossing` responde "sim" enquanto o IP Aero move o vagão. |
| `IPSableBridgeMixin` | `IPSableBridge.moveThroughPortal` do IP Aero | **HEAD:** descarta as rodas fantasmas, guarda o estado do vagão e quem está nos controladores, e marca a nave como "em teleporte". **RETURN:** passa o trem para o mundo novo, corrige as posições internas, agenda o re-encaixe e devolve os controladores. |
| `IPShipJointsMixin` | `IPShipJoints.shiftLoadedJoints` do IP Aero | Roda uma vez por grupo de naves amarradas, depois que todas se moveram, e desloca o parceiro de cada Knuckle. |
| `AnalogControllerServerMixin` | `AnalogControllerBlockEntity.serverTick` do Loconautics | Mantém o usuário por até 100 ticks enquanto ele está em outro mundo. |
| `AnalogControllerClientMixin` (cliente) | `AnalogControllerClientHandler` do Loconautics | Troca a posição do controlador em silêncio e segura o controle durante a travessia. |
| `RedstoneLinkPairMixin` | `RedstoneLinkPair.deliverCurrent` do Loconautics | Calcula o alcance do Redstone Link pela posição real da nave. |
| `ConsistTurnsMixin` | `ConsistTurns.isDriver` do Loconautics | Conta como "dirigido" o vagão cujo Bearing Axle recebe comando de virar. |
| `DiagnosticsConfigMixin`, `TurnMemoryMixin` | diagnóstico e memória de desvios do Loconautics | Só usados pelo `/locoip debug`. |

O re-encaixe (`PendingRerail`) roda no tick do servidor logo depois da mudança:
1. acha o trilho embaixo do vagão;
2. monta um carriage provisório com o referencial antigo do vagão;
3. assenta os truques, mantendo os trilhos virtuais que já tinham atravessado para este mundo;
4. devolve velocidade e acelerador, trocando o sinal se o trilho novo correr no sentido contrário.

Depois que o último vagão do trem é assentado, o `StraddledJunctions`:
1. apaga as escolhas de desvio velhas;
2. percorre o trilho de truque em truque;
3. grava cada desvio entre eles com o lado do truque da frente.

## Como compilar

Não há Gradle. O `build.sh` compila com `javac` contra os jars de uma instância real do modpack.

Você precisa de:
- bash
- `unzip`
- JDK 21
- uma instância NeoForge 1.21.1 com os mods acima
- a pasta `libraries` do launcher

```bash
INST="/caminho/da/instancia/mods" LIB="/caminho/do/launcher/libraries" JDK="/caminho/do/jdk-21/bin" ./build.sh
```

O jar sai na raiz do projeto como `loconautics_ip_compat-<versão>.jar`. A versão vem de `res/META-INF/neoforge.mods.toml`.

## Estrutura do projeto

```
src/locoipcompat/
  LocoIpCompat.java            entrada do mod (tick do servidor, comando /locoip)
  PendingRerail.java           estado do trem na mudança + re-encaixe no lugar
  StraddledJunctions.java      desvios entre truques depois da travessia
  ControllerHandoff.java       usuários do Analog Controller na mudança
  KnuckleHandoff.java          engates Knuckle na mudança
  LinkRange.java               alcance de Redstone Link para blocos dentro de naves
  LocoDebug.java               /locoip debug
  client/ClientControllerHandoff.java
  mixin/                       os mixins da tabela acima
res/
  META-INF/neoforge.mods.toml
  loconautics_ip_compat.mixins.json
  pack.mcmeta
build.sh
```

## Compatibilidade

- O mod depende de **classes internas e campos privados** do Loconautics, que ainda está em beta, e do IP Aero.
- Se algum dos dois atualizar, recompile contra os jars novos e teste uma travessia.
- Se algo que o mod usa sumir, o log avisa (`[LocoIP] a versão do Loconautics mudou…`). Essa parte da correção é pulada, sem derrubar o jogo.
- Testado com pares de portais do Nether. Portais que giram o trem devem funcionar, mas não foram testados.

## Problemas conhecidos

- **Quem está em pé escorrega um pouco para trás:** o jogador em pé no trem (sem banco) escorrega um pouco na travessia, mais quanto mais rápido. Ele passa pelo portal um instante antes do trem e fica em cima da "imagem" do trem que o IP Aero cria do outro lado. Sentado num banco isso não deve acontecer.
- **Desvio colado na saída do portal:**
  - Se o trilho passa pelo portal (trilho de portal do Create), o trem segue o lado escolhido durante a travessia, então dá para virar comandando antes do portal.
  - Se os trilhos de cada lado são separados, o trem chega reto e segue o lado que está embaixo dele.
- **A primeira viagem para uma área nova** pode travar o servidor por um instante enquanto os chunks do destino são gerados. É a geração do mundo, não este mod.

## Licença

MIT, como declarado no `neoforge.mods.toml`.
