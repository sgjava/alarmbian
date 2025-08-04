![Title](images/title.png)

Alarmbian is a cross platform DIY NVR at its core, but it can also be used to
build your own smart cameras. The cool thing is you can use almost any board
Armbian supports, an x86 mini PC, an old x86 based PC and probably Windows
since the code is based on Java, FFMPEG, OpenCV and Deepstack. All testing is
on Ubuntu 24.04 at this point.

This project is at the beginning stages, but the event system and database
logging of events is in place. The plan is to add REST services in order to get
data from the camera servers and have a client be able to use that data. I've
already built one based on an older version of Alarmbian.
* Low power and small footprint ODROID-XU4 handles six 4K/15 FPS H265+ streams.
* Motion detection built in with the ability to add other types of realtime detection.
* History image shows entire motion event in a single image.
* Use Deepstack or SenseAI.

I'm leaving optimized install up to the user since there are so many ways to optimize FFMPEG, OpenCV, Deepstack, etc. This is truly a DIY system.
I'll point you in the right direction hopefully.

## Install FFMPEG
This is the center of the camera stream universe and where hardware acceleration
can be very important. FFMPEG is used to stream off the high resolution video and
can also be used to read the substream frames in OpenCV, etc. The nice thing is
that if you copy the stream to disk there are no decode/encode steps to slow
things down. Thus it's easy to stream multiple 4K cameras to disk using low end
ARM based hardware. In my case I'm using H265+.
* FFMPEG generic `sudo apt install pkg-config` then `sudo apt install ffmpeg` if
hardware acceleration is in the package or you want generic version
* FFMPEG from [source](https://trac.ffmpeg.org/wiki/CompilationGuide/Ubuntu)
* FFMPEG [Intel QuickSync](https://gist.github.com/feedsbrain/0191516b5625b577c2b14241cff4fe30)
* FFMPEG CUDA [build-ffmpeg](https://github.com/markus-perl/ffmpeg-build-script)
warning: this will build static libs

## Install Java
* `cd`
* [Download](https://www.azul.com/downloads/?package=jdk#download-openjdk) Zulu JDK 21 for your platform using the .tar.gz
* `tar -xf zulu*`
* `rm *.tar.gz`
* `sudo mkdir -p /usr/lib/jvm`
* `sudo mv zulu* /usr/lib/jvm/jdk21`
* `sudo nano /etc/environment`
* Modify PATH and append `:/usr/lib/jvm/jdk21/bin` to the end
* Add `JAVA_HOME="/usr/lib/jvm/jdk21"` on new line
* Save file
* Close shell and open a new one
* `java -version`

## Install Ant
* `cd`
* [Download](https://ant.apache.org/bindownload.cgi) latest Maven bin.tar.gz
* `tar -xf apache-ant*`
* `rm *.tar.gz`
* `sudo mv apache-ant* /opt/ant`
* `sudo nano /etc/environment`
* Modify PATH and append `:/opt/ant/bin` to the end
* Save file
* Close shell and open a new one
* `ant -version`

## Install OpenCV
There are several ways to install OpenCV such as my [script](https://github.com/sgjava/install-opencv)
or [Video IO hardware acceleration](https://github.com/opencv/opencv/wiki/Video-IO-hardware-acceleration),
but select what is optimized for your platform. The main thing is to build the Java bindings. I typically
include Python, Java and C/C++ just to cover all the bases. Python is good for quick and dirty
prototyping. If you do run my install you only need to do the following because Java is installed: 
* `cd`
* `git clone --depth 1 https://github.com/sgjava/install-opencv.git`
* `cd install-opencv/scripts`
* Edit `config.sh` and make changes as needed
* `./install-libjpeg-turbo.sh`
* `./install-opencv.sh`
* Check *.log files

## Install Supervisor
Supervisor will be used to start all the jobs up required for Alarmbian. We
will place all logs in ~/logs.
* `cd`
* `mkdir logs`
* `sudo apt install supervisor`

## Install MediaMTX
It makes sense to centralize camera streams and minimize traffic from the cameras.
mediamtx makes this happen. Cameras like the Annke C800 only allow one
connection to the substream, so a proxy is required. Substreams are used for
analysis and live viewing, so more than one stream at a time is required.
* `cd`
* [Download](https://github.com/bluenviron/mediamtx/releases) latest .tar.gz file
* `tar -xf mediamtx*`
* `rm *.tar.gz`
* `nano mediamtx.yml`
* `protocols: [tcp]`
* Edit `paths` section to specify your substreams
* `./mediamtx`
* Test proxy on client
* ^C to exit
Add Supervisor job
* Reference [configuration](scripts/supervisor/mediamtx.conf)
* `sudo nano /etc/supervisor/conf.d/mediamtx.conf`
* `sudo supervisorctl update`
* Test proxy on client
* Check logs dir for issues

## H2 database
H2 is used to store data from the Alarmbian application. Other data stores could
be used as well with configuration and schema.sql changes,
* `cd`
* [Download](http://www.h2database.com/html/download.html) latest jar file (use Binary JAR link)
* Example `wget -O h2-2.3.232.jar https://search.maven.org/remotecontent?filepath=com/h2database/h2/2.3.232/h2-2.3.232.jar`
* `java -cp h2*.jar org.h2.tools.Server -baseDir ~/ -tcp -web -ifNotExists -tcpAllowOthers`
* Start another shell on same machine
* `java -cp h2*.jar org.h2.tools.Shell -driver org.h2.Driver -url jdbc:h2:tcp://localhost/nio:test -user sa -password sa`
* `quit`
* `ls -al test.mv.db`
* ^C to exit server shell
* `rm test.mv.db`
Add Supervisor job
* Reference [configuration](server/scripts/supervisor/h2.conf)
* `sudo nano /etc/supervisor/conf.d/h2.conf`
* `sudo supervisorctl update`
* Test H2 client
* Check logs dir for issues

## Install Maven
* `cd`
* [Download](https://maven.apache.org/download.cgi) latest Maven bin.tar.gz
* `tar -xf apache-maven*`
* `rm *.tar.gz`
* `sudo mv apache-maven* /opt/maven`
* `sudo nano /etc/environment`
* Modify PATH and append `:/opt/maven/bin` to the end
* Add `M2_HOME="/opt/maven"` on new line
* Save file
* Close shell and open a new one
* `mvn -version`

## Download project
* `sudo apt install git`
* `cd`
* `git clone --depth 1 https://github.com/sgjava/alarmbian.git`
* `cd alarmbian`
* Edit [POM](https://github.com/sgjava/alarmbian/blob/72e7bf59daf5b959c3bb6e710d9660549715e765/server/pom.xml#L21)
and change `opencv` as needed
* Edit [POM](https://github.com/sgjava/alarmbian/blob/72e7bf59daf5b959c3bb6e710d9660549715e765/server/pom.xml#L23)
and change `opencv.lib` as needed
* `mvn initialize`
* `mvn clean install`
* `cp server/target/server-1.0.0-SNAPSHOT.jar ~/.`
* `cd`
* `sudo supervisorctl start h2`
* `sudo supervisorctl start mediamtx`
* Use [application.properties](https://raw.githubusercontent.com/sgjava/alarmbian/main/src/main/resources/application.properties)
to make your cam1.properties configuration
* `java -Djava.library.path=/home/username/opencv/build/lib -jar server-1.0.0-SNAPSHOT.jar --spring.config.location=cam1.properties`
* ^C to exit app
* If you see a SIGSEGV don't worry because ^C will not be used for shutdown.
Add Supervisor job
* Reference [configuration](server/scripts/supervisor/cam1.conf)
* `sudo nano /etc/supervisor/conf.d/cam1.conf`
* `sudo supervisorctl update`
* Check logs dir for issues

## Install Docker and run as non-root user
* `sudo apt install apt-transport-https ca-certificates curl software-properties-common`
* `curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg`
* `echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null`
* `sudo apt update`
* `sudo apt install docker-ce`
* `sudo usermod -aG docker ${USER}`
* Close shell and open a new one
* `docker version` 

## Deepstack
This is for optional Deepstack 2.0 support. This can be run from a different system
if needed.There is no longer support for ARM32, but all the rest of the stack
supports ARM32.Use ARM64 or x86_64 to run Deepstack. Make sure you use the correct
 version of `docker run` if you want hardware acceleration, etc. Below is the CPU
based version.

Please note Deepstack is very CPU intensive without hardware acceleration. Trying to
run multiple cameras and Deepstack on the same low end hardware will result in
poor performance.
* ARM64
  * `docker run --detach --restart unless-stopped -e VISION-DETECTION=True -e MODE=Medium -v localstorage:/datastore -p 5000:5000 --name deepstack deepquestai/deepstack:arm64`
* x86_64
  * `docker run --detach --restart unless-stopped -e VISION-DETECTION=True -e MODE=Medium -v localstorage:/datastore -p 5000:5000 --name deepstack deepquestai/deepstack`
* `docker update --restart unless-stopped deepstack`
To stop
* `docker stop deepstack`
To remove volume
* `docker volume rm localstorage`

# Use case 1 ROCK64
Going the SBC route can be a bit more difficult than using an old X86 based
desktop and Ubuntu. The main advantages are price, size and power consumption.
* ROCK64 V3 SBC with 4G RAM
* VIA Labs, Inc. VL711 SATA 6Gb/s bridge (USB 3 port)
* KingDian 480GB 3D NAND 2.5 Inch SSD
* Realtek Semiconductor Corp. RTL8153 Gigabit Ethernet Adapter (USB 2 port)
* Raspberry Pi heat sink and 12V fan powered off ROCK64's 5V/GND pins

In this instance I'm testing the full stack including Deepstack object detection.
one 4K camera with motion detection uses about 9% CPU. Once frames are sent to
Deepstack it will spike to 80%. Detection is about 2 FPS since the code
serializes calls. 



