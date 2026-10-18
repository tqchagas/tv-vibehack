# TCL C6K — configuração pessoal

TV identificada por ADB como TCL G08 / Smart TV Pro, Android 14 (SDK 34).
Launcher original: `com.google.android.apps.tv.launcherx/.home.HomeActivity`.
TV ao vivo: `com.tcl.tv`.

Dock, da esquerda para a direita: YouTube, Stremio, YouCine, SportzX, Netflix, Prime Video e HBO Max.
Aplicativos a preservar: YouTube, Stremio, Netflix, Globoplay, Prime Video, HBO Max, SportzX e YouCine.
Configuração em `launcher/app/src/main/res/values/config.xml`.
Os textos acompanham o idioma do sistema; há tradução para português.
Visual compacto: sete colunas, fundo azul fixo, dock na metade inferior, área superior livre para wallpaper e sem título grande ou brilho dinâmico.
TV ao vivo, PS5 (quando a entrada CEC está presente e não desconectada), Globoplay e Play Store ficam na segunda linha.
O PS5 abre a HDMI 1; o ID observado está em `ps5_input_id` e deve ser atualizado se o console mudar de porta.
A data e a hora ficam à esquerda; Entradas, rede e Configurações ficam à direita. Os outros atalhos estão apenas
ocultos em `hidden_apps`; seus aplicativos e serviços continuam instalados e habilitados.

## Instalação

Com o ambiente Python descrito no README principal, JDK 17, Android SDK 34 e
build-tools 34.0.0, execute a partir da raiz do repositório:

```bash
export TV_HOST=192.168.5.162
export JAVA_HOME=/caminho/para/jdk17/Contents/Home
export ANDROID_HOME=/caminho/para/android-sdk
bash examples/tcl-c6k/install.sh
```

O script compila, verifica a transferência e a instalação, abre o launcher e
confere a resolução da tela inicial. Se o firmware continuar priorizando o Google TV,
desativa `com.google.android.apps.tv.launcherx` e
`com.google.android.tungsten.setupwraith` para o usuário 0. Neste firmware,
a atividade de recuperação do segundo pacote interfere no botão Home quando
o Google TV é desativado. Esse pacote também contém funções de configuração
e vinculação de contas; reabilite-o antes de usar essas funções.
Aguarda a transição do firmware e verifica novamente a resolução da Home.
O launcher original permanece instalado e pode ser reabilitado pelo script de restauração.
Os demais aplicativos e serviços permanecem habilitados.
Na primeira instalação, confira a tela e a navegação com o controle remoto.

## Voltar ao Google TV

```bash
TV_HOST=192.168.5.162 bash examples/tcl-c6k/restore.sh
```

O script reabilita o assistente de configuração e o launcher original, define sua atividade como tela inicial
e abre a Home. O novo launcher continua instalado para uso posterior.

## Verificação na TV

Compilação e instalação verificadas, incluindo a integridade da transferência.
Os sete aplicativos da primeira linha abriram pelo dock e retornaram à tela
inicial pelo botão Home. A grade e o menu de entradas foram conferidos por
capturas de tela. Os oito aplicativos informados permanecem habilitados.
A restauração ao Google TV também foi executada e verificada antes de
reaplicar o launcher personalizado pelo script de instalação.

## Arquivos locais

O inventário anterior às alterações está em `backup/tcl-c6k/baseline.txt`.
As capturas e o ambiente de compilação ficam em `backup/`, ignorado pelo Git.
O inventário pode conter identificadores do aparelho e dados da rede; mantenha-o local.

## Alterar o visual

Para trocar a ordem do dock, edite `dock_apps` no arquivo de configuração e
recompile. O fundo azul é definido em `launcher/app/src/main/res/drawable/home_background.xml`; não
há seletor de papel de parede na interface.
