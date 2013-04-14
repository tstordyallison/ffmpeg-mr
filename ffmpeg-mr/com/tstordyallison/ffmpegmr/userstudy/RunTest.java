package com.tstordyallison.ffmpegmr.userstudy;

import java.awt.Frame;
import java.io.File;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import uk.co.caprica.vlcj.binding.LibVlc;
import uk.co.caprica.vlcj.binding.internal.libvlc_media_t;
import uk.co.caprica.vlcj.component.EmbeddedMediaPlayerComponent;
import uk.co.caprica.vlcj.player.MediaPlayer;
import uk.co.caprica.vlcj.player.MediaPlayerEventListener;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.runtime.RuntimeUtil;

import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;

public class RunTest {
	
	private static class TimeLimiter implements MediaPlayerEventListener{
        	long startPosition = Long.MAX_VALUE;
        	long stopAfter = 30000;

			@Override
			public void videoOutput(MediaPlayer mediaPlayer, int newCount) {
			}
			@Override
			public void titleChanged(MediaPlayer mediaPlayer, int newTitle) {
			}
			@Override
			public void timeChanged(MediaPlayer mediaPlayer, long newTime) {
				if(newTime > startPosition + stopAfter)
					mediaPlayer.stop();
			}
			@Override
			public void subItemPlayed(MediaPlayer mediaPlayer, int subItemIndex) {
			}
			@Override
			public void subItemFinished(MediaPlayer mediaPlayer, int subItemIndex) {
			}
			@Override
			public void stopped(MediaPlayer mediaPlayer) {
			}
			@Override
			public void snapshotTaken(MediaPlayer mediaPlayer, String filename) {
			}
			@Override
			public void seekableChanged(MediaPlayer mediaPlayer, int newSeekable) {
			}
			@Override
			public void positionChanged(MediaPlayer mediaPlayer, float newPosition) {

			}
			
			@Override
			public void playing(MediaPlayer mediaPlayer) {
				mediaPlayer.setTime(startPosition);
			}
			
			@Override
			public void paused(MediaPlayer mediaPlayer) {
			}
			
			@Override
			public void pausableChanged(MediaPlayer mediaPlayer, int newSeekable) {
			}
			
			@Override
			public void opening(MediaPlayer mediaPlayer) {
			}
			
			@Override
			public void newMedia(MediaPlayer mediaPlayer) {
			}
			
			@Override
			public void mediaSubItemAdded(MediaPlayer mediaPlayer, libvlc_media_t subItem) {
			}
			
			@Override
			public void mediaStateChanged(MediaPlayer mediaPlayer, int newState) {
			}
			
			@Override
			public void mediaParsedChanged(MediaPlayer mediaPlayer, int newStatus) {
			}
			
			@Override
			public void mediaMetaChanged(MediaPlayer mediaPlayer, int metaType) {
			}
			
			@Override
			public void mediaFreed(MediaPlayer mediaPlayer) {
			}
			
			@Override
			public void mediaDurationChanged(MediaPlayer mediaPlayer, long newDuration) {
			}
			
			@Override
			public void mediaChanged(MediaPlayer mediaPlayer) {
			}
			
			@Override
			public void lengthChanged(MediaPlayer mediaPlayer, long newLength) {
			}
			
			@Override
			public void forward(MediaPlayer mediaPlayer) {
			}
			
			@Override
			public void finished(MediaPlayer mediaPlayer) {
			}
			
			@Override
			public void error(MediaPlayer mediaPlayer) {
			}
			
			@Override
			public void endOfSubItems(MediaPlayer mediaPlayer) {
			}
			@Override
			public void buffering(MediaPlayer mediaPlayer) {
			}
			@Override
			public void backward(MediaPlayer mediaPlayer) {
			}
			
		}
	private static class RunWhenStopped implements MediaPlayerEventListener{
    	Runnable runWhenStopped;

    	public RunWhenStopped(Runnable runWhenStopped)
    	{
    		this.runWhenStopped = runWhenStopped;
    	}
    	
		@Override
		public void videoOutput(MediaPlayer mediaPlayer, int newCount) {
		}
		@Override
		public void titleChanged(MediaPlayer mediaPlayer, int newTitle) {
		}
		@Override
		public void timeChanged(MediaPlayer mediaPlayer, long newTime) {
		}
		@Override
		public void subItemPlayed(MediaPlayer mediaPlayer, int subItemIndex) {
		}
		@Override
		public void subItemFinished(MediaPlayer mediaPlayer, int subItemIndex) {
		}
		@Override
		public void stopped(MediaPlayer mediaPlayer) {
			runWhenStopped.run();
		}
		@Override
		public void snapshotTaken(MediaPlayer mediaPlayer, String filename) {
		}
		@Override
		public void seekableChanged(MediaPlayer mediaPlayer, int newSeekable) {
		}
		@Override
		public void positionChanged(MediaPlayer mediaPlayer, float newPosition) {

		}
		
		@Override
		public void playing(MediaPlayer mediaPlayer) {
		}
		
		@Override
		public void paused(MediaPlayer mediaPlayer) {
		}
		
		@Override
		public void pausableChanged(MediaPlayer mediaPlayer, int newSeekable) {
		}
		
		@Override
		public void opening(MediaPlayer mediaPlayer) {
		}
		
		@Override
		public void newMedia(MediaPlayer mediaPlayer) {
		}
		
		@Override
		public void mediaSubItemAdded(MediaPlayer mediaPlayer, libvlc_media_t subItem) {
		}
		
		@Override
		public void mediaStateChanged(MediaPlayer mediaPlayer, int newState) {
		}
		
		@Override
		public void mediaParsedChanged(MediaPlayer mediaPlayer, int newStatus) {
		}
		
		@Override
		public void mediaMetaChanged(MediaPlayer mediaPlayer, int metaType) {
		}
		
		@Override
		public void mediaFreed(MediaPlayer mediaPlayer) {
		}
		
		@Override
		public void mediaDurationChanged(MediaPlayer mediaPlayer, long newDuration) {
		}
		
		@Override
		public void mediaChanged(MediaPlayer mediaPlayer) {
		}
		
		@Override
		public void lengthChanged(MediaPlayer mediaPlayer, long newLength) {
		}
		
		@Override
		public void forward(MediaPlayer mediaPlayer) {
		}
		
		@Override
		public void finished(MediaPlayer mediaPlayer) {
		}
		
		@Override
		public void error(MediaPlayer mediaPlayer) {
		}
		
		@Override
		public void endOfSubItems(MediaPlayer mediaPlayer) {
		}
		@Override
		public void buffering(MediaPlayer mediaPlayer) {
		}
		@Override
		public void backward(MediaPlayer mediaPlayer) {
		}
		
	}
	
	private static class TestEntry{
		public TestEntry(String mrl, long start) {
			this.mrl = new File(mrl).getAbsolutePath();
			this.start = start;
		}
		public String mrl;
		public long start;
		public String toString(){
			return mrl;
		}
	}
	
	private static EmbeddedMediaPlayer mediaPlayer;
	private static TimeLimiter limiter = new TimeLimiter();
	private static JFrame frame;
	private static int candidateNumber;
	
	private static String[] preClipMessages = new String[]{
		"Press OK to begin playing the 1st clip from video 1.",
		"Press OK to begin playing the 2nd clip from video 1.",
		"Please fill in the questionaire for 'Video 1'.\n------\nWhen you are done, press OK to begin playing the 1st clip from video 2.",
		"Press OK to begin playing the 2nd clip from video 2.",
		"Please fill in the questionaire for 'Video 2'.\n------\nWhen you are done, press OK to exit."
	};

	private static int[][] candidateOrders = new int[][] {
		{1, 0, 2, 3},
		{3, 2, 1, 0},
		{2, 3, 1, 0},
		{1, 0, 3, 2},
		{2, 3, 0, 1},
		{0, 1, 2, 3},
		{3, 2, 0, 1},
		{0, 1, 3, 2}
	};
	private static TestEntry[] playlist  = new TestEntry[] {new TestEntry("userstudy/Test3.mp4.mkv", 1145000),
															new TestEntry("userstudy/Test3FFmpeg.mp4.mkv", 1145000), 
															new TestEntry("userstudy/AvatarCloud3k.mkv", 98000), 
															new TestEntry("userstudy/AvatarCloud3kFFmpeg.mkv", 98000)};
	private static int playListPosition = 0;
	
	public static void main(String[] args) {
		NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), "/Applications/VLC.app/Contents/MacOS/lib");
		Native.loadLibrary(RuntimeUtil.getLibVlcLibraryName(), LibVlc.class);
		
	    SwingUtilities.invokeLater(new Runnable() {
				@Override
	            public void run() {
	            	// Figure out the candidate number and the playlist.
	            	candidateNumber = Integer.parseInt(JOptionPane.showInputDialog("Please enter the candidate ID."));
	            	int[] candidateOrder = candidateOrders[((candidateNumber-1) % 8) ];
	            	
	            	TestEntry[] newPlaylist  = new TestEntry[playlist.length];
	            	for(int i = 0; i < newPlaylist.length; i++)
	            		newPlaylist[i] = playlist[candidateOrder[i]];
	            	playlist = newPlaylist;

	            	// Setup the GUI.
	            	EmbeddedMediaPlayerComponent c = new EmbeddedMediaPlayerComponent();
	            	mediaPlayer = c.getMediaPlayer();
	            	
	            	setLookandFeel();
	            	frame = new JFrame("FFmpeg-MR User Study");
	                frame.setLocation(50, 50);
	                frame.setSize(1400, 800);
	                frame.setExtendedState(Frame.MAXIMIZED_BOTH); 
	                frame.setContentPane(c);
	                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	                frame.setUndecorated(true);
	                frame.setVisible(true);
	                
	                Runnable playlistAdvancer = new Runnable() {
						@Override
						public void run() {
							if(playListPosition < playlist.length){
								limiter.startPosition = playlist[playListPosition].start;
								limiter.stopAfter = 50000L;
								JOptionPane.showMessageDialog(frame, preClipMessages[playListPosition]);
								mediaPlayer.playMedia(playlist[playListPosition].mrl);
								playListPosition +=1;
							}
							else {
								JOptionPane.showMessageDialog(frame, preClipMessages[playListPosition]);
								System.exit(0);
							}
						}
					};

					mediaPlayer.addMediaPlayerEventListener(new RunWhenStopped(playlistAdvancer));
	                mediaPlayer.addMediaPlayerEventListener(limiter);
	                playlistAdvancer.run(); 
	            }
	        });
	}
	
	private static void setLookandFeel(){
    	try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException e) {
		} catch (InstantiationException e) {
		} catch (IllegalAccessException e) {
		} catch (UnsupportedLookAndFeelException e) {
		}
	}

}