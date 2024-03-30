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
:h, :help - Display this help, scroll with e and s
:u, :users - focus on the users list, enable scrolling with e and s
:c, :chat - back to the chat in live reload focus
:m, :msg - on the chat, enable scrolling through the messages with e and s
:w, :whisper <username> - goes to the private discussion view with the other user (TODO)
:r <lines> <columns> - Resize the views
:new <path> - Create a codex from a file or directory                             (WIP)
:share <SHA-1> - Share a codex with the given SHA-1                               (TODO)
:unshare <SHA-1> - Unshare a codex with the given SHA-1                           (TODO)
:mycdx cdx - Display the list of your codex
:cdx <SHA-1> - Retrieves and display the codex info with the given SHA-1          (TODO)
:exit - Exit the application                                                      (WIP)
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
