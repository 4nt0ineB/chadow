# Chadow-BASTOS-SEBBAH

Implementation of the Chadow protocole described in [rfc_chadow.txt](./rfc_chadow.txt)

http://igm.univ-mlv.fr/coursprogreseau/tds/projet2024.html


### run
```sh
# find your terminal size first
$ stty size 
25 238
$ java -jar --enable-preview target/chadow-1.0.0.jar localhost 7777 25 238
```

### Chadow client CLI
By default, the main view is in live reload and shows the new messages and connected users in real time.
Because it needs to clear up the screen, the user can't type commands or messages at the same time 
('live reload' mode is indicated by the user's login being greyed out).
The user has to press enter to switch to 'input mode' (the user's login is then shown in color).

The input field allows multiline input. It works by writing the character \ before pressing enter. 
In 'message mode' any input that is not a command is considered as message to be sent to the server.

The user can type the following commands:

```shell
##  ┓┏  ┓
##  ┣┫┏┓┃┏┓
##  ┛┗┗━┗┣┛
##       ┛

'scrollable':
  e - scroll one page up
  s - scroll one page down
  r - scroll one line up
  d - scroll one line down
  t - scroll to the top
  b - scroll to the bottom
  
'selectable' (is scrollable):
  y - move selector up
  h - move selector down
  :s, :see - Select the item
  
[GLOBAL COMMANDS]
  :h, :help - Display this help (scrollable)
  :c, :chat - Back to the [CHAT] in live reload
  :w, :whisper <username> (message)- Start a new private discussion with a user
    if (message) is present, send the message also
  :d - Update and draw the display
  :r <lines> <columns> - Resize the view
  :new <codexName>, <path> - Create a codex from a file or directory
    and display the details of new created [CODEX] info (mind the space between , and <path>)
  
  :mycdx - Display the list of your codex (selectable)
  :cdx:<SHA-1> - Retrieves and display the [CODEX] info with the given SHA-1
    if the codex is not present locally, the server will be interrogated        (TODO)
  :exit - Exit the application                                                  (WIP)
  
[CHAT]
  when the live reload is disabled (indicated by the coloured input field)
  any input not starting with ':' will be considered as a message to be sent
  
  :m, :msg - Focus on chat (scrollable)
  :u, :users - Focus on the users list (scrollable)
  
[PRIVATE MESSAGES]
  :m, :msg - Focus on the chat (scrollable)
  :w - Enables back live reload
  
[CODEX]
(scrollable)
:share - Share/stop sharing the codex
:download - Download/stop downloading the codex
        
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