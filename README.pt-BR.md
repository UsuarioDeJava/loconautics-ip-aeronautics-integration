# Loconautics × Immersive Portals: Aeronautics

[English](README.md) | **Português (Brasil)**

Este é um mod de compatibilidade para NeoForge 1.21.1. Com ele, os trens do **Loconautics** atravessam portais do **Immersive Portals: Aeronautics** (IP Aero) inteiros. O trem não descarrila e nenhum vagão some. Os engates continuam presos e você não perde o controle.

Quem faz a travessia é o *straddle* do IP Aero, que leva a nave do trem de um mundo para o outro. Este mod desliga a travessia própria do Loconautics e conserta o que quebraria do outro lado.

> Add-on não oficial. Ele não tem ligação com os autores do Loconautics, do Immersive Portals: Aeronautics, do Sable nem do Create.

---

## O que ele faz

- **Um só sistema de travessia.** A travessia de portal do Loconautics (`TrainPortalCrossing`) é desligada, então o "gêmeo" e o corte com fade não aparecem mais. Sem isso, os dois mods brigam pelo trem.
- **O trem sobrevive à mudança.** O Loconautics costuma apagar o trem quando a nave sai do mundo. Enquanto o IP Aero move o vagão, ele fica marcado como "atravessando" e o trem é mantido.
- **As rodas fantasmas ficam para trás.** As rodas fantasmas do Loconautics são naves separadas, presas a cada vagão. Elas são removidas logo antes da travessia, e o Loconautics monta rodas novas do outro lado. Se atravessassem soltas, o vagão batia nelas e era arremessado do trilho.
- **As coordenadas internas são corrigidas.** O IP Aero desloca as coordenadas internas da nave (o plot) quando ela muda de mundo, e o mod acerta o que o Loconautics guarda:
  - a posição dos truques, que é relativa ao centro do plot;
  - os pontos de frente e de trás do vagão, que são coordenadas absolutas do plot;
  - o parceiro de cada engate;
  - a posição dos controladores.
- **Re-encaixe no lugar.** Cada vagão é assentado no trilho logo embaixo dele, sem recriar a nave. Era a recriação que fazia o trem sumir para quem olhava pelo portal.
- **A marcha continua igual.** A velocidade, o acelerador, o sentido de marcha e o estado de parada em estação ficam como estavam antes do portal.
- **Os engates seguram.** Vagões ligados por **Knuckle** continuam engatados. A referência de cada parceiro acompanha o deslocamento do seu vagão, e vagões engatados continuam sem colidir entre si.
- **O controle manual continua.**
  - O **Analog Controller** fica com o mesmo jogador, com o mesmo acelerador e as mesmas teclas seguradas.
  - Se o jogador e o trem ficarem por um instante em mundos diferentes, o controlador espera até 5 segundos antes de soltar.
  - No cliente, as teclas nunca voltam para o jogador no meio da travessia, então você não sai andando de repente.

## Requisitos

Testado com:

| Mod | Versão |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.265 |
| Loconautics | 0.1.2 BETA |
| Immersive Portals: Aeronautics | 2.8.27 |
| Sable | 2.0.6 |
| Create | 6.0.10 |
| Create Aeronautics | 1.3.2 |

O Loconautics e o Immersive Portals: Aeronautics são dependências **obrigatórias**. Outras versões podem funcionar, mas o mod usa partes internas dos dois (veja [Compatibilidade](#compatibilidade)).

## Instalação

1. Baixe o jar na página de [Releases](../../releases), ou compile você mesmo (veja abaixo).
2. Coloque o `loconautics_ip_compat-<versão>.jar` na pasta `mods/`.
   - Instale no **cliente e no servidor**: parte da correção roda em cada lado.
3. Abra o jogo. Não há nada para configurar.

## Uso

Monte o trem do Loconautics como sempre e passe por um portal do IP Aero, com o trilho continuando do outro lado.
- Funciona com trens montados em estação e com vagões engatados por Knuckle.
- Você pode dirigir pelo Analog Controller ou por controles externos (redstone, horários).
- Funciona nos dois sentidos, quantas vezes quiser.

O mod escreve no log uma linha para cada coisa que faz, sempre começando com `[LocoIP]`. Exemplos:

```
[LocoIP] vagão 3ae44c9e levado pelo Immersive Portals para minecraft:the_nether; assentar no trilho agendado
[LocoIP] vagão 3ae44c9e assentado no trilho em minecraft:the_nether (1 truque(s), velocidade 27.3 b/s, acelerador 28.0 b/s)
[LocoIP] engate do vagão 8289414d continua preso ao 3ae44c9e do outro lado do portal
[LocoIP] controle manual em 10, 120, 20 continua com Player do outro lado do portal
```

## Como funciona

Todos os ganchos são mixins; não há arquivo de configuração.

| Mixin | Alvo | O que faz |
| --- | --- | --- |
| `TrainPortalCrossingMixin` | `TrainPortalCrossing` do Loconautics | Cancela o `tick`. Faz o `isCrossing` responder "sim" enquanto o IP Aero move o vagão, para o `SubLevelSplitWatcher` não apagar o trem. |
| `IPSableBridgeMixin` | `IPSableBridge.moveThroughPortal` do IP Aero | **HEAD:** descarta as rodas fantasmas e guarda o estado do vagão (centro, velocidade, acelerador, referencial do trilho) e quem está nos controladores. Também marca a nave como "em teleporte" para o Loconautics e solta rodas e contatos. **RETURN:** passa o trem para o mundo novo, corrige truques e frente/trás, agenda o re-encaixe e devolve os controladores. |
| `IPShipJointsMixin` | `IPShipJoints.shiftLoadedJoints` do IP Aero | Roda uma vez por grupo de naves amarradas, depois que todas se moveram. Desloca a posição do parceiro de cada Knuckle pelo deslocamento desse parceiro. |
| `AnalogControllerServerMixin` | `AnalogControllerBlockEntity.serverTick` do Loconautics | Mantém o usuário enquanto ele está em outro mundo, por até 100 ticks. |
| `AnalogControllerClientMixin` (cliente) | `AnalogControllerClientHandler` do Loconautics | Troca a posição do controlador em silêncio e reencontra o controlador dentro da nave movida. Segura o controle, com as teclas presas, durante a travessia. |

O re-encaixe (`PendingRerail`) roda no tick do servidor, um tick depois da mudança:
1. Acha o trilho embaixo do vagão.
2. Monta um carriage provisório com o referencial antigo do vagão, para ele não virar 180°.
3. Assenta cada truque no trilho embaixo dele.
4. Devolve velocidade e acelerador, trocando o sinal se o sentido do trilho novo for o contrário.

## Como compilar

Não há Gradle. O `build.sh` compila com `javac` contra os jars de uma instância real do modpack.

Você precisa de:
- bash
- `unzip`
- JDK 21
- uma instância NeoForge 1.21.1 com os mods da tabela acima
- a pasta `libraries` do launcher

```bash
INST="/caminho/da/instancia/mods" LIB="/caminho/do/launcher/libraries" JDK="/caminho/do/jdk-21/bin" ./build.sh
```

- O jar sai na raiz do projeto como `loconautics_ip_compat-<versão>.jar`.
- A versão vem de `res/META-INF/neoforge.mods.toml`.
- Sem as variáveis, o script usa os caminhos escritos no começo dele.
- A pasta `build/` guarda cópias dos jars de outros mods e não vai para o repositório.

## Estrutura do projeto

```
src/locoipcompat/
  LocoIpCompat.java            entrada do mod (gancho do tick do servidor)
  PendingRerail.java           estado do trem na mudança + re-encaixe no lugar
  ControllerHandoff.java       usuários do Analog Controller na mudança
  KnuckleHandoff.java          engates Knuckle na mudança
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
  - Se um método que ele usa sumir, o log avisa (`[LocoIP] a versão do Loconautics mudou…`). Essa parte da correção é pulada, sem derrubar o jogo.
- Foi testado com pares de portais do Nether, que mantêm a orientação. Portais que giram o trem devem funcionar, mas não foram testados.

## Problemas conhecidos

- **Quem está em pé escorrega um pouco para trás.**
  - O jogador em pé no trem, sem banco, escorrega um pouco para trás na travessia. Quanto mais rápido o trem, maior o escorregão.
  - Isso acontece porque o jogador passa pelo portal uma fração de segundo antes do trem. Nesse instante ele fica em cima da "imagem" do trem que o IP Aero cria do outro lado, e essa imagem não o arrasta junto.
  - Sentado num banco isso não deve acontecer.
- **A primeira viagem para uma área nova** pode travar o servidor por um instante, enquanto os chunks do destino são gerados. É a geração do mundo, não este mod.

## Licença

MIT, como declarado no `neoforge.mods.toml`.
