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
Because it needs to clear up the screen, meaning that the user can't type commands or messages at the same time 
('live reload' mode is indicated by the user's login being greyed out)
The user has to press enter to enter the 'input' mode. (the user's login is then shown in color);

The input field allows multiline input. It works by escaping by writing '\' before pressing enter. 
In 'message mode' any input that is not a command is considered as message to be sent to the server.

The user can type the following commands:

| Command       | Description                                                                                     |
|---------------|-------------------------------------------------------------------------------------------------|
| :m (:message) | select the default mode (message mode) described above                                          |
| :c (:chat)    | to scroll on the chat (stops live reload), scroll with e (up) and s (down) |
| :u (:users)   | to scroll on list of connected users (stops live reload), scroll with e (up) and s (down)       |


@Todo<br>
:q :quit<br> 
:h :help<br>




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
