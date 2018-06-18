#!/usr/bin/env sh

SHORT_DESCRIPTION="Netmon - network monitor"
APPNAME=netmon
TEMPFILE=/tmp/installer-app.desktop
BINARY=./netmon-uber.jar
DEST=~/opt/jarapps/$APPNAME
DESTFILE=netmon.jar 
ICON=./resources/network-online.jpg

echo "PWD = "$(pwd)

mkdir -p $DEST

# Copy executable to installation directory 
cp -v $BINARY $DEST/$DESTFILE

# Copy Icon to installation directory
cp -v $ICON $DEST 

cat <<EOF >> $TEMPFILE
[Desktop Entry]
Name=$SHORT_DESCRIPTION
Exec=java -jar $DEST/$DESTFILE
Icon=$DEST/network-online.jpg
Type=Application
Terminal=false
Categories=Tools;
EOF

# Display desktop file 
cat $TEMPFILE

# Create desktop shortcut 
cp -v $TEMPFILE ~/Desktop/$APPNAME.desktop

# Install into user Menu 
cp -v $TEMPFILE ~/.local/share/applications/$APPNAME.desktop 

rm -rf $TEMPFILE
echo "Removed temporary file "$TEMPFILE
