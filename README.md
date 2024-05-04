# Chadow-BASTOS-SEBBAH 
[![Public server instance - 217.182.68.48:7777](https://img.shields.io/badge/Public_server_instance-217.182.68.48%3A7777-green?logo=rocket)](https://gitlab.com/4nt0ineB/chadow-bastos-sebbah/)

[Final project](http://igm.univ-mlv.fr/coursprogreseau/tds/projet2024.html) for the Network Programming class in the first year of a Master's degree in computer science.

Implementation of the Chadow protocole described in [rfc_chadow.txt](./rfc_chadow.txt) (french)






### Build
```sh
mvn package
```
It builds an uber jar in the target folder. It contains all the dependencies for the client and the server.

## Chadow server CLI

### run
The server require settings. you can use -h or --help to see the usage.
```sh
$ java -jar --enable-preview target/chadow-1.0.0.jar -h
```
You start the app as a server by using the --server option.

ex:
```sh
$ java -jar --enable-preview target/chadow-1.0.0.jar --server 7777
```

The linux script `server_linux_run.sh` is provided to run the server locally on port 7777.
```shell
$ chmod +x server_linux_run.sh
$ ./server_linux_run.sh (username)
```

## Chadow client CLI

By default, the main view is in live refresh and shows the new messages and connected users in real time.
Because it needs to clear up the screen, the user can't type commands or messages at the same time 
('live refresh' mode is indicated by the user's login being greyed out).
The user has to press enter to switch to 'input mode' (the user's login is then shown in color).

The input field allows multiline input. It works by writing the character \ before pressing enter. 
In 'message mode' any input that is not a command is considered as message to be sent to the server.

Available commands are described in the client by typing :h or :help.

Find you terminal size. <br>
On linux:
```sh
$ stty size
25 238
```

### run
The client require settings. you can use -h or --help to see the usage.
```sh
$ java -jar --enable-preview target/chadow-1.0.0.jar -h
```
ex:

```sh
$ java -jar --enable-preview target/chadow-1.0.0.jar John localhost 7777 25 238
```
You can also not provide the terminal size, the server will use the default size 25 80.
You will be able to change the size later with the command :resize (or :r) in the client. 
``` :r 25 238 ```

The linux script `run.sh` is provided to run the client with the right parameters for a server running locally 
on localhost 7777.
```shell
$ chmod +x client_linux_run.sh
# will run the client with a random username and the default server settings localhost 7777
# You can override the username by providing it as an argument
$ ./client_linux_run.sh (username)
```

```sh

### Commands

```sh
##  ┓┏  ┓
##  ┣┫┏┓┃┏┓
##  ┛┗┗━┗┣┛
##       ┛

User Interaction:
- When your [username] is greyed out, your input is disabled.
- Press enter to switch to input mode and enable your input. Your [username] will be colored.
- The input field allows multiline input. It works by writing the character \ before pressing enter.s

Scrollable mode:
  e - scroll one page up
  s - scroll one page down
  r - scroll one line up
  d - scroll one line down
  t - scroll to the top
  b - scroll to the bottom
  
Selectable mode (also scrollable):
  y - move selector up
  h - move selector down
  :s, :select - Select the item
  ! scrolling also moves the selector !
  
[GLOBAL COMMANDS]
  :h, :help
    Display this help (scrollable)
    
  :c, :chat
    Back to the [CHAT] in live refresh
    
  :w, :whisper <username> (message)
    Create and display a new DM with a user. If (message) is present,
    send the message also
    
  :ws,:whispers
    Display the list of DM [Direct Message list]
    
  :d
    Update and draw the display
    
  :r <lines> <columns>
    Resize the view
    
  :new <codexName>, <path>
    Create a codex from a file or directory and display the [CODEX]
    and display the details of new created [CODEX] info
    
  :f, :find [:at(:before|:after)) <date>] [(name|date):(asc|desc)] <name>
    Interrogate the server for codexes
    
  :f - Back to the last search results
  
  :mycdx
    Display the [CODEX LIST]
    
  :cdx:<SHA-1>
    Retrieves and display the [CODEX] info with the given SHA-1
    if the codex is not present locally, the server will be interrogated
    
  :exit - Exit the application
  
[CHAT]
  when the live refresh is disabled (indicated by the coloured input field)
  any input not starting with ':' will be considered as a message to be sent
  
  :m, :msg - Focus on chat (scrollable)
  :u, :users - Focus on the users list (scrollable)
  
[DM list]
  (selectable)
  :s, :select - Select the direct message
  :rm - Delete the focused discussion
  
[DM]
  :m, :msg - Enable scrolling (scrollable)
  :w - Enables chat mode
  :delete - Delete the discussion
  
[CODEX]
  (scrollable)
  :share - Share/stop sharing the codex
  :dl, :download (h|hidden)
    Download/stop downloading the codex, when downloading live refresh is enabled
  :live - Switch to live refresh to see the changes in real time
```



### Sources

RFC:
https://www.ietf.org/rfc/rfc3285.txt
https://github.com/discord/discord-api-docs/blob/main/docs/resources/User.md

On torrent:
https://igm.univ-mlv.fr/~dr/XPOSE2013/bittorrent/index.html

https://wiki.theory.org/BitTorrentSpecification

https://www.reddit.com/r/AskComputerScience/comments/t3o14/how_do_peers_find_each_other_in_a_p2p_network/

https://www.bleepingcomputer.com/news/security/sha1-collision-attack-can-serve-backdoored-torrents-to-track-down-pirates/

https://stackoverflow.com/questions/47331092/how-bittorrent-tracker-works