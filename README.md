![Title](images/title.png)

Alarmbian is a cross platform DIY NVR at its core, but it can also be used to
build your own smart cameras. The cool thing is you can use almost any board
Armbian supports, an x86 mini PC, an old x86 based PC and probably Windows
since the code is based on Java, MediaMTX, FFMPEG and OpenCV.
All testing is on Ubuntu 24.04 at this point.

Unlike other NVR software, Alarmbian can handle h265+ or any stream FFMPEG can handle.
It's event driven and built for modern or old cameras. I've added an SMTP server that
cameras can send images to. This is handy with the camera's built-in AI detection.
* Low power and small footprint ODROID-XU4 handles six 4K/15 FPS H265+ streams.
* Motion detection built in with the ability to add other types of realtime detection.
* History image shows entire motion event in a single image.
* Images sent via AI detection handled.
* Java based UI to view events and play video.

I'm leaving optimized install up to the user since there are so many ways to optimize FFMPEG, OpenCV, etc. This is truly
a DIY system. I'll point you in the right direction hopefully.

## Install FFMPEG
Install hardware acceleration libraries like Cuda before running script.
* `cd ~/alarmbian/scripts`
* `./install-ffmpeg.sh`

## Install Supervisor
Supervisor will be used to start all the jobs up required for Alarmbian. We
will place all logs in ~/logs. Make sure you edit individual conf files and change
`username` to your actual username.
* `cd`
* `mkdir logs`
* `sudo apt install supervisor`

## Install MediaMTX
It makes sense to centralize camera streams and minimize traffic from the cameras.
MediaMTX makes this happen. Cameras like the Annke C800 only allow one
connection to the substream, so a proxy is required. Substreams are used for
analysis and live viewing, so more than one stream at a time is required.
* `cd`
* [Download](https://github.com/bluenviron/mediamtx/releases) latest .tar.gz file to the home directory
* `cd`
* `tar -xf mediamtx*`
* `rm *.tar.gz`
* `nano mediamtx.yml`
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

## Clone project
* `cd`
* `git clone --depth 1 https://github.com/sgjava/alarmbian.git`

## Install Java
* `cd ~/alarmbian/scripts`
* `./install-java.sh`

## H2 database
H2 is used to store data from the Alarmbian application. Other data stores could
be used as well with configuration and schema.sql changes,
* `cd`
* [Download](http://www.h2database.com/html/download.html) latest jar file (use Binary JAR link)
* Example `wget -O h2-2.4.240.jar https://search.maven.org/remotecontent?filepath=com/h2database/h2/2.4.240/h2-2.4.240.jar`
* `java -cp h2*.jar org.h2.tools.Server -baseDir ~/ -tcp -web -ifNotExists -tcpAllowOthers`
* Start another shell on same machine
* `java -cp h2*.jar org.h2.tools.Shell -driver org.h2.Driver -url jdbc:h2:tcp://localhost/nio:test -user sa -password sa`
* `quit`
* `ls -al test.mv.db`
* ^C to exit server shell
* `rm test.mv.db`

Add Supervisor job
* Reference [configuration](scripts/supervisor/h2.conf)
* `sudo nano /etc/supervisor/conf.d/h2.conf`
* `sudo supervisorctl update`
* Test H2 client
* Check logs dir for issues

## Install OpenCV
Modify script to customize install for hardware acceleration, etc.
* `cd ~/alarmbian/scripts`
* `./install-opencv.sh`

## Build project
* `cd ~/alarmbian`
* `mvn initialize -Pinstall-opencv`
* `mvn clean install`
* `cp server/target/server-1.0.0-SNAPSHOT.jar ~/.`
* `cp server/target/smtp-1.0.0-SNAPSHOT.jar ~/.`
* `cd`
* `sudo supervisorctl start h2`
* `sudo supervisorctl start mediamtx`
* Use [application.properties](https://raw.githubusercontent.com/sgjava/alarmbian/refs/heads/main/server/src/main/resources/application.properties)
to make your cam1.properties configuration (repeat for each camera using unique file name)
* `java -Djava.library.path=/home/username/opencv/build/lib -jar server-1.0.0-SNAPSHOT.jar --spring.config.location=cam1.properties`
* ^C to exit app
* If you see a SIGSEGV don't worry because ^C will not be used for shutdown.
Add Supervisor job (repeat for each camera using unique file name)
* Reference [configuration](scripts/supervisor/cam1.conf)
* `sudo nano /etc/supervisor/conf.d/cam1.conf`
* `sudo supervisorctl update`
* Check logs dir for issues
Add Supervisor job
* Reference [configuration](scripts/supervisor/smtp.conf)
* `sudo nano /etc/supervisor/conf.d/smtp.conf`
* `sudo supervisorctl update`
* Check logs dir for issues

## Play UI
Play UI is a [UiBooster](https://github.com/Milchreis/UiBooster) based UI that uses OpenCV and ffplay to view events and play videos.
You can set before and after seconds to see what was before and after event. You can also save event as video.
![Client](images/client.png)
* `sudo apt install sshfs`
* `sudo mkdir /mnt/data1` or whatever local dir you want to use
* `sudo chown username:username /mnt/data1` change username to your local user
* `sshfs servadmin@192.168.1.99:/data1/ /mnt/data1` change ip, remote and local dirs as needed
* `cd alarmbian` this assumes you compiled project on the UI machine
* Use [application.properties](https://raw.githubusercontent.com/sgjava/alarmbian/refs/heads/main/client/src/main/resources/application.properties) to make your own client.properties
* `java -Djava.library.path=/home/username/opencv/build/lib -Dspring.config.location=file:cam1.properties -jar client/target/client-1.0.0-SNAPSHOT.jar`
