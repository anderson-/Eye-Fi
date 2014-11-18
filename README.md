Eye-Fi 
===

What is it? 
---
Eye-Fi is a secure file transfer software directed for anonymously sharing data across notebooks. It doesn't use no intermediary media, no physical connection between the computers or a computer network, no radio communication (Wi-Fi, Bluetooth), or any other conventional mean. The only things needed is a notebook with a webcam.

How it works? 
---

The concept is very simple, two notebooks are placed facing each other, each one recording the screen of the other. One is labeled "Server" and it has the file, answering requests on demand, the other is the "Client" and it is responsible to setup the connection parameters, request chunks of data, and handling any connection problem.

![](https://raw.github.com/anderson-/eye-fi/master/src/eyefi/slogo128.png)

The data transfer is made by swapping QR codes, encoded with chunks of the original file. For encoding an QR code two parameters are needed: Version and error correction level (ECL). Higher versions encode more data, but are harder to read. Higher ECL encode less data, but the message can be reconstructed if a portion of the QR code is not visible.

The process of sending a file over screen-webcam is described as follows:

![](https://raw.github.com/anderson-/eye-fi/master/doc/CommunicationProtocol.png)


Features 
---

- Tested on Windows and Linux
- Auto scan for best QR code version and error correction level
- Auto reduce/increase QR code version during transfer
- Real time transfer statistics
- MD5Sum check for file integrity
- Simulate file transfer locally
- Simulate webcam interference
- Open source: GPLv3

|        **Pros**         |  **Cons**   |
|:-----------------------:|:-----------:|
|Secure                   |Awkward setup|
|Awesome and fun?         |Slow         |

**2014 - Anderson de Oliveira Antunes**