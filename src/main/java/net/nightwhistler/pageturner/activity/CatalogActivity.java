/*
 * Copyright (C) 2012 Alex Kuiper
 * 
 * This file is part of PageTurner
 *
 * PageTurner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PageTurner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PageTurner.  If not, see <http://www.gnu.org/licenses/>.*
 */
package net.nightwhistler.pageturner.activity;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Stack;
import java.util.UUID;

import net.nightwhistler.htmlspanner.HtmlSpanner;
import net.nightwhistler.nucular.atom.Entry;
import net.nightwhistler.nucular.atom.Feed;
import net.nightwhistler.nucular.atom.Link;
import net.nightwhistler.nucular.parser.Nucular;
import net.nightwhistler.pageturner.R;
import net.nightwhistler.pageturner.catalog.CatalogListAdapter;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import roboguice.activity.RoboActivity;
import roboguice.inject.InjectView;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.inject.internal.Nullable;
import com.markupartist.android.widget.ActionBar;
import com.markupartist.android.widget.ActionBar.Action;

public class CatalogActivity extends RoboActivity implements OnItemClickListener {
		
	private String baseURL;
	
	private CatalogListAdapter adapter;
	
	private ProgressDialog waitDialog;
	private ProgressDialog downloadDialog;
	
	private static final Logger LOG = LoggerFactory.getLogger(CatalogActivity.class); 
	
	private Stack<String> navStack = new Stack<String>();
	
	@Nullable
	@InjectView(R.id.actionbar)
	private ActionBar actionBar;
	
	private Action prevAction;
	private Action homeAction;
	private Action nextAction;
	private Action searchAction;	
	
	@InjectView(R.id.catalogList)
	@Nullable
	private ListView catalogList;
		
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		setContentView(R.layout.catalog);		
		initActionBar();
		
		this.adapter = new DownloadingCatalogAdapter();
		catalogList.setAdapter(adapter);
		catalogList.setOnItemClickListener(this);
		
		this.waitDialog = new ProgressDialog(this);
        this.waitDialog.setOwnerActivity(this);  
        
        this.downloadDialog = new ProgressDialog(this);
        
        this.downloadDialog.setIndeterminate(false);
        this.downloadDialog.setMax(100);
        this.downloadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        this.downloadDialog.setCancelable(true);
                
		super.onCreate(savedInstanceState);
		
		this.baseURL = getIntent().getStringExtra("url");		
		
		new LoadOPDSTask().execute(baseURL);
	}
	
	private void initActionBar() {
		     
	     actionBar.setTitle(R.string.download);	
	    
	     this.homeAction = new ActionBar.AbstractAction(R.drawable.home) {
				
				@Override
				public void performAction(View view) {
					onNavClick( this );				
				}
		     };
		 actionBar.setHomeAction(this.homeAction);
	     
	     this.prevAction = new ActionBar.AbstractAction(R.drawable.arrow_left) {
			
			@Override
			public void performAction(View view) {
				onNavClick( this );				
			}
	     };
	     
	     this.nextAction = new ActionBar.AbstractAction(R.drawable.arrow_right) {
				
				@Override
				public void performAction(View view) {
					onNavClick( this );				
				}
		  };
		  
		  this.searchAction = new ActionBar.AbstractAction(R.drawable.zoom) {
				
				@Override
				public void performAction(View view) {
					onNavClick( this );				
				}
		  };
	     
	    
	}
	
	@Override
	public void onItemClick(AdapterView<?> list, View arg1, int position, long arg3) {		
		
		Entry entry = adapter.getItem(position);
		if ( entry.getAtomLink() != null && ! navStack.contains(entry.getAtomLink().getHref() )) {
			String href = entry.getAtomLink().getHref();
			loadURL(href);		
		} else {
			loadFakeFeed(entry);
		}
	}
	
	private boolean isLeafEntry(Feed feed, Entry entry ) {
		return feed.getEntries().size() == 1;
	}
	
	
	
	private void loadFakeFeed( Entry entry ) {
		String base = baseURL;
		
		if ( ! navStack.isEmpty() ) {
			base = navStack.peek();
		}
		
		navStack.push(base);
		new LoadFakeFeedTask(entry).execute(base);
	}
	
	private void loadURL( String url ) {
		
		String base = baseURL;
		
		if ( ! navStack.isEmpty() ) {
			base = navStack.peek();
		}
		
		try {
			String target = new URL(new URL(base), url).toString();
			LOG.info("Loading " + target );
			
			navStack.push(target);
			new LoadOPDSTask().execute(target);
		} catch (MalformedURLException u ) {
			LOG.error("Malformed URL:", u);
		}
	}
	
	public void onNavClick( Action a ) {
		if ( a == homeAction ) {
			navStack.clear();
			new LoadOPDSTask().execute(baseURL);
			return;
		} else if ( a == nextAction ) {
			loadURL( adapter.getFeed().getNextLink().getHref() );
		} else if ( a == prevAction ) {
			if ( navStack.size() > 0 ) {
				onBackPressed(); 
			} else if ( adapter.getFeed().getPreviousLink() != null ) {
				loadURL( adapter.getFeed().getPreviousLink().getHref() );
			} 
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuItem item = menu.add( getString(R.string.open_library) );
		item.setIcon(R.drawable.book_star);
		
		item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
			
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				finish();
				return true;
			}
		});
		
		return true;
	}
	
	@Override
	protected void onStop() {		
		downloadDialog.dismiss();
		waitDialog.dismiss();
		
		super.onStop();
	}
	
	@Override
	public void onBackPressed() {
		if ( navStack.isEmpty() ) {
			finish();
		} else {
			navStack.pop();
		}
		
		if ( navStack.isEmpty() ) {
			new LoadOPDSTask().execute(baseURL);
		} else {
			new LoadOPDSTask().execute(navStack.peek());
		}
	}
	
	private class DownloadingCatalogAdapter extends CatalogListAdapter {
		
		HtmlSpanner spanner = new HtmlSpanner();
				
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View rowView;		
			final Entry entry = getItem(position);

			LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			Link imgLink;

			if ( isLeafEntry(adapter.getFeed(), entry) ) {			
				rowView = inflater.inflate(R.layout.catalog_download, parent, false);	
				
				Button button = (Button) rowView.findViewById( R.id.readButton );
				TextView authorTextView = (TextView) rowView.findViewById(R.id.itemAuthor);
				
				button.setOnClickListener(new View.OnClickListener() {
					
					@Override
					public void onClick(View v) {
						try {
							String base = baseURL;
							
							if ( ! navStack.isEmpty() ) {
								base = navStack.peek();
							}
							
							URL url = new URL(new URL(base), entry.getEpubLink().getHref());
							new DownloadFileTask().execute(url.toExternalForm());
						} catch (Exception e) {
							throw new RuntimeException(e);
						}					
					}
				});
				
				if ( entry.getAuthor() != null ) {				
					String authorText = String.format( getString(R.string.book_by),
						 entry.getAuthor().getName() );
					authorTextView.setText( authorText );
				} else {
					authorTextView.setText("");
				}
				
				if ( entry.getEpubLink() == null ) {
					button.setVisibility(View.INVISIBLE);
				} else {
					button.setVisibility(View.VISIBLE);
				}
				
				imgLink = entry.getImageLink();
				
			} else {
				rowView = inflater.inflate(R.layout.catalog_item, parent, false);
				imgLink = entry.getThumbnailLink();
			}
					
			
			TextView title = (TextView) rowView.findViewById(R.id.itemTitle);
			TextView desc = (TextView) rowView.findViewById(R.id.itemDescription );
			
			ImageView icon = (ImageView) rowView.findViewById(R.id.itemIcon);

			if ( imgLink != null && imgLink.getBinData() != null ) {			
				byte[] data = imgLink.getBinData();
				icon.setImageBitmap(BitmapFactory.decodeByteArray(data, 0, data.length));
			} else {
				icon.setImageDrawable(getResources().getDrawable(R.drawable.book));
			}

			title.setText(entry.getTitle());

			if ( entry.getContent() != null ) {
				desc.setText( spanner.fromHtml( entry.getContent().getText() ));
			} else if ( entry.getSummary() != null ){
				desc.setText( spanner.fromHtml( entry.getSummary()));
			} else {
				desc.setText("");
			}

			return rowView;
		}	
	}
	
	//this is our download file asynctask
    private class DownloadFileTask extends AsyncTask<String, Integer, String> {
    	
    	File destFile;
    	
    	private Exception failure;
       
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            downloadDialog.setMessage(getString(R.string.downloading));
            downloadDialog.show();
        }

       
        @Override
        protected String doInBackground(String... params) {

            try {
            	
            	String url = params[0];
            	LOG.debug("Downloading: " + url);
            	
            	String fileName = url.substring( url.lastIndexOf('/') + 1 );
            	
            	DefaultHttpClient client = new DefaultHttpClient();
    			HttpGet get = new HttpGet( url );
    				
   				HttpResponse response = client.execute(get); 
   				
   				if ( response.getStatusLine().getStatusCode() == 200 ) {

   					File destFolder = new File("/sdcard/PageTurner/Downloads/");
   					if ( ! destFolder.exists() ) {
   						destFolder.mkdirs();
   					}

   					destFile = new File(destFolder, URLDecoder.decode(fileName) );

   					if ( destFile.exists() ) {
   						destFile.delete();
   					}

   					//lenghtOfFile is used for calculating download progress
   					long lenghtOfFile = response.getEntity().getContentLength();

   					//this is where the file will be seen after the download
   					FileOutputStream f = new FileOutputStream(destFile);
   					//file input is from the url
   					InputStream in = response.getEntity().getContent();

   					//here's the download code
   					byte[] buffer = new byte[1024];
   					int len1 = 0;
   					long total = 0;

   					while ((len1 = in.read(buffer)) > 0) {

   						//Make sure the user can cancel the download.
   						if ( isCancelled() ) {
   							return null;
   						}

   						total += len1; 
   						publishProgress((int)((total*100)/lenghtOfFile));
   						f.write(buffer, 0, len1);
   					}
   					f.close();
   				} else {
   					this.failure = new RuntimeException( response.getStatusLine().getReasonPhrase() );
   					LOG.error("Download failed: " + response.getStatusLine().getReasonPhrase() );	
   				}
               
            } catch (Exception e) {
            	LOG.error("Download failed.", e);	
            	this.failure = e;
            }
           
            return null;
        }
        
        @Override
        protected void onProgressUpdate(Integer... values) {
        	downloadDialog.setProgress(values[0]);
        }       

        @Override
        protected void onPostExecute(String unused) {
           downloadDialog.hide();
           
           if ( ! isCancelled() && failure == null ) {
        	   Intent intent = new Intent(getBaseContext(), ReadingActivity.class);   		
        	   intent.setData( Uri.parse(destFile.getAbsolutePath()));   				   				
        	   startActivity(intent);
        	   finish();
           } else if ( failure != null ) {
        	   Toast.makeText(CatalogActivity.this, R.string.book_failed, Toast.LENGTH_LONG).show();
           }
        }
    }   
    
    private void loadImageLink( Link imageLink, String baseUrl ) throws IOException {
    	
    	DefaultHttpClient client = new DefaultHttpClient();		
    	
    	if ( imageLink != null ) {
			String href = imageLink.getHref();					

			if ( href.startsWith("data:image/png;base64")) {
				String dataString = href.substring( href.indexOf(',') + 1 );
				try {
					imageLink.setBinData( Base64.decode(dataString, Base64.DEFAULT ));
				} catch ( NoClassDefFoundError ncd ) {
					//Slight hack for Android 2.1
					imageLink.setBinData(null);
				}
			} else {
				String target = new URL(new URL(baseUrl), href).toString();	
				
				LOG.info("Downloading image: " + target );
				
				HttpResponse resp = client.execute(new HttpGet(target));

				imageLink.setBinData( EntityUtils.toByteArray( resp.getEntity() ) );
			}
		}
    }
	
    private void setNewFeed( Feed result ) {
    	
    	if ( actionBar == null ) {
    		return;
    	}
    	
    	actionBar.removeAllActions();			
		
		if ( result != null ) {
			
			if ( result.getPreviousLink() != null || navStack.size() > 0 ) {
				actionBar.addAction(prevAction);
			}
			
			if ( result.getNextLink() != null ) {
				actionBar.addAction(nextAction);
			}
			
			actionBar.setTitle( result.getTitle() );
			adapter.setFeed(result);

			waitDialog.hide();
		} else {
			waitDialog.hide();
			Toast.makeText(CatalogActivity.this, R.string.feed_failed, Toast.LENGTH_LONG ).show();
		}
    }
    
    private class LoadFakeFeedTask extends AsyncTask<String, Integer, Feed> {
    	
    	private Entry singleEntry;
    	
    	public LoadFakeFeedTask(Entry entry) {
    		this.singleEntry = entry;
		}
    	
    	@Override
		protected void onPreExecute() {
			waitDialog.setTitle(getString(R.string.loading_wait));
	    	waitDialog.show();
		}
    	
    	@Override
    	protected Feed doInBackground(String... params) {
    		Feed fakeFeed = new Feed();
    		fakeFeed.addEntry(singleEntry);
    		fakeFeed.setTitle(singleEntry.getTitle());
    		
    		try {
    			loadImageLink(singleEntry.getImageLink(), params[0]);
    		} catch (IOException io) {
    			LOG.error("Could not load image: ", io);
    		}
    		
    		return fakeFeed;
    	}
    	
    	@Override
    	protected void onPostExecute(Feed result) {
    		setNewFeed(result);
    	}
    }
    
	private class LoadOPDSTask extends AsyncTask<String, Integer, Feed> implements OnCancelListener {
		
		@Override
		protected void onPreExecute() {
			waitDialog.setTitle(getString(R.string.loading_wait));
			waitDialog.setOnCancelListener(this);
	    	waitDialog.show();
		}
		
		@Override
		public void onCancel(DialogInterface dialog) {
			this.cancel(true);			
		}
		
		@Override
		protected Feed doInBackground(String... params) {
			
			String baseUrl = params[0];
			
			if ( baseUrl == null || baseUrl.trim().length() == 0 ) {
				return null;
			}
			
			baseUrl = baseUrl.trim();
			
			try {
				DefaultHttpClient client = new DefaultHttpClient();
				HttpGet get = new HttpGet( baseUrl );
				
				HttpResponse response = client.execute(get);
				Feed feed = Nucular.readFromStream( response.getEntity().getContent() );
				
				for ( Entry entry: feed.getEntries() ) {
					
					if ( isCancelled() ) {
						return feed;
					}
					
					Link imageLink;
					
					if( isLeafEntry(feed, entry) ) {
						imageLink = entry.getImageLink();
					} else {
						imageLink = entry.getThumbnailLink();
					}
					
					loadImageLink(imageLink, baseUrl);					
				}				
				
				return feed;
			} catch (Exception e) {
				LOG.error("Download failed.", e);
				return null;
			}				
			
		}		
		
		@Override
		protected void onPostExecute(Feed result) {
			setNewFeed(result);			
		}
		
	}
	
}
