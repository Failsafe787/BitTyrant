<p align="center">
  <img src="https://raw.githubusercontent.com/subwave07/BitTyrant/master/README.md_images/header.jpg"><br/><br/>
</p>

<p align="center">
  <a href="#overview">Overview</a> |
  <a href="#screenshot">Screenshot</a> |
  <a href="#downloads">Downloads</a> |
  <a href="#papers">Papers</a> |
  <a href="#people">People</a> |
  <a href="#acknowledgments">Acknowledgments</a> |
  <a href="http://bittyrant.cs.washington.edu/press.html">Press</a>
</p>

***This is a backup mirror of the client source code, currently unavailable for download at the [official website](http://bittyrant.cs.washington.edu) of this project.
I ([subwave07](https://github.com/subwave07)) am neither one of the developers nor I have contibuited to this project in any way. All the credits go to the original developers.
The following text is the recreation of the webpage of the project snapshot***

## Overview
BitTyrant is a new, protocol compatible BitTorrent client that is optimized for fast download performance. 

BitTyrant is...

* Fast – During evaluation testing on more than 100 real BitTorrent swarms, BitTyrant provided an average 70% download performance increase when compared to the existing Azureus 2.5 implementation, with some downloads finishing more than three times as quickly.
* Fair – BitTorrent was designed with incentives in mind: if a user is downloading at 30 KBps, they should upload at 30 KBps. However, due to the unique workload properties of many real-world swarms, this is not always enforced. BitTyrant is designed to make efficient use of your scarce upload bandwidth, rewarding those users whose upload allocations are fair and only allocating excess capacity to other users. 
* Familiar – BitTyrant is based on modifications to Azureus 2.5, currently the most popular BitTorrent client. All of our changes are under the hood. You’ll find the GUI identical to Azureus, with optional additions to display statistics relevant to BitTyrant’s operation.

For details about these claims, check out the information below.

## FAQ

*Q: Isn't BitTyrant just another leeching client?*

No. BitTyrant does not change the amount of data uploaded, just which peers receive that data. Specifically, peers which upload more to you get more of your bandwidth. When all peers use the BitTyrant client as released, performance improves for the entire swarm. The details of this are explained further below. In our paper, we consider situations in which peers use clients which attempt to both maximize performance and conserve upload contribution, but BitTyrant, as released, attempts only to maximize performance.

*Q: How is BitTyrant different from existing BitTorrent clients?*

BitTyrant differs from existing clients in its selection of which peers to unchoke and send rates to unchoked peers. Suppose your upload capacity is 50 KBps. If you’ve unchoked 5 peers, existing clients will send each peer 10 KBps, independent of the rate each is sending to you. In contrast, BitTyrant will rank all peers by their receive / sent ratios, preferentially unchoking those peers with high ratios. For example, a peer sending data to you at 20 KBps and receiving data from you at 10 KBps will have a ratio of 2, and would be unchoked before unchoking someone uploading at 10 KBps (ratio 1). Further, BitTyrant dynamically adjusts its send rate, giving more data to peers that can and do upload quickly and reducing send rates to others.

*Q: Will BitTyrant work for cable / DSL users?*

Yes. Although the evaluation in our paper focuses on users with slightly higher upload capacity than is typically available from US cable / DSL providers today, BitTyrant’s intelligent unchoking and rate selection still improves performance for users with less capacity. All users, regardless of capacity, benefit from using BitTyrant.

*Q: Won’t BitTyrant hurt overall BitTorrent performance if everyone uses it?*

This is a subtle question and is treated most thoroughly in the paper. The short answer is: maybe. A big difference between BitTyrant and existing BitTorrent clients is that BitTyrant can detect when additional upload contribution is unlikely to improve performance. If a client were truly selfish, it might opt to withhold excess capacity, reducing performance for other users that would have received it. However, our current BitTyrant implementation always contributes excess capacity, even when it might not improve performance. *Our goal is to improve performance, not minimize upload contribution.*

## Screenshot

<p align="center">
  <img src="https://raw.githubusercontent.com/subwave07/BitTyrant/master/README.md_images/screenshot.jpg"><br/>
  Download peer information with per-connection ratios and BitTyrant status.
</p>

## Downloads

**Note:** BitTyrant is research software. Although we have used it internally for several months, it is likely to still have several bugs and performance quirks that we have not yet identified. If you’ve found a bug, please [send us a note](http://www.cs.washington.edu/htbin-post/unrestricted/mailto2.pl?to=BitTyrant;sub=BitTyrant+bug).

BitTyrant requires [Java 1.5](http://www.java.com/). If you're having problems getting things started, [try updating your JVM](http://java.sun.com/javase/downloads/index.jsp).

**Important:** For BitTyrant to be most effective, it is crucial that you accurately [set your upload capacity](http://bittyrant.cs.washington.edu/capacity_config.html) during configuration.

Version: 1.1.1 (Released 7 September, 2007) ([changelog](http://bittyrant.cs.washington.edu/changelog.txt))

| | | |
|-|-|-|
| ![OSX Logo](https://raw.githubusercontent.com/subwave07/BitTyrant/master/README.md_images/osx.jpg) | [Mac OS X disk image](http://bittyrant.cs.washington.edu/dist_090607/BitTyrant.dmg) (Req. 10.4) | MD5: 422de23cc335ffb1086e30329e337cde |
| ![Windows Logo](https://raw.githubusercontent.com/subwave07/BitTyrant/master/README.md_images/windows.jpg) | [Windows installer](http://bittyrant.cs.washington.edu/dist_090607/Azureus_2.5.0.0_BitTyrant_Win32.setup.exe) | MD5: d7741898dcb73a5d600d797fc56f4dc4 |
| ![Linux Logo](https://raw.githubusercontent.com/subwave07/BitTyrant/master/README.md_images/tux.jpg) | [32-bit Linux JAR](http://bittyrant.cs.washington.edu/dist_090607/BitTyrant-Linux32.tar.bz2) and [Installation instructions](http://azureus.sourceforge.net/howto_linux.php) | MD5: 44a65aa40d8a3e3aa2e90452522ec679 |
| ![Linux Logo](https://raw.githubusercontent.com/subwave07/BitTyrant/master/README.md_images/tux.jpg) | [64-bit Linux JAR](http://bittyrant.cs.washington.edu/dist_090607/BitTyrant-Linux64.tar.bz2) | MD5: eafa51b8d6ee0c0f3309c738f1a63142 |

The BitTyrant source code is also [available](http://coblitz.codeen.org:3125/bittyrant.cs.washington.edu/dist_010807/BitTyrant-src.zip). Anonymous trace data collected during our study is available by request.

## Papers
Do incentives build robustness in BitTorrent? [ [pdf ](http://www.cs.washington.edu/homes/piatek/papers/BitTyrant.pdf)]

[Michael Piatek](http://www.cs.washington.edu/homes/piatek/), [Tomas Isdal](http://isd.al/), [Thomas Anderson](http://www.cs.washington.edu/homes/tom/), [Arvind Krishnamurthy](http://www.cs.washington.edu/homes/arvind/), [Arun Venkataramani](http://www.cs.umass.edu/~arun/)

4th USENIX Symposium on Networked Systems Design & Implementation ([NSDI 2007](http://www.usenix.org/events/nsdi07/index.html)).

A poster and talk regarding BitTyrant were presented in the 2006 UW CSE Affiliates meeting.

## People

[Contact us](http://www.cs.washington.edu/htbin-post/unrestricted/mailto2.pl?to=BitTyrant;sub=BitTyrant)

| *Graduate students* | *Undergraduates* | *Faculty* |
|-|-|-|
| [Michael Piatek](http://www.cs.washington.edu/homes/piatek/) | [Jarret Falkner](http://jarret.falkfalk.com/) | [Tom Anderson](http://www.cs.washington.edu/homes/tom/) |
| [Tomas Isdal](http://isd.al/) | | [Arvind Krishnamurthy](http://www.cs.washington.edu/homes/arvind/) |
| | | [Arun Venkataramani](http://www.cs.umass.edu/~arun/) | 

## Acknowledgments

This work is supported by the [NSF](http://www.nsf.gov/) (CNS-0519696), the [ARCS Foundation](http://www.arcsfoundation.org/Seattle/), and [UW CSE](http://www.cs.washington.edu/).

| | |
|-|-|
| [![Azureus Logo](https://raw.githubusercontent.com/subwave07/BitTyrant/master/README.md_images/azureus_splash.jpg)](http://azureus.sourceforge.net/) | BitTyrant is based on modifications to the Azureus 2.5 code base. |
| [![Emulab Logo](https://raw.githubusercontent.com/subwave07/BitTyrant/master/README.md_images/emulab.jpg)](http://www.emulab.net/) | Initial testing of BitTyrant was conducted on the Emulab testbed. |
| [![Planetlab Logo](https://raw.githubusercontent.com/subwave07/BitTyrant/master/README.md_images/planetlab.jpg)](http://www.planet-lab.org/) | BitTyrant’s wide-area evaluation was performed using the PlanetLab testbed. |
| [![Coblitz Logo](https://raw.githubusercontent.com/subwave07/BitTyrant/master/README.md_images/coblitz.gif)](http://codeen.cs.princeton.edu/coblitz/) | The BitTyrant software distribution is mirrored using the CoBlitz file distribution service. |
