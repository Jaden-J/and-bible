package net.bible.android.view.activity.bookmark;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.bookmark.Bookmark;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.bookmark.LabelDto;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

/**
 * Choose Document (Book) to download
 * 
 * NotificationManager with ProgressBar example here:
 * http://united-coders.com/nico-heid/show-progressbar-in-notification-area-like-google-does-when-downloading-from-android
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class Bookmarks extends ListActivityBase {
	private static final String TAG = "Bookmarks";

	static final String BOOKMARK_ID_EXTRA = "bookmarkId";
	static final String LABEL_NO_EXTRA = "labelNo";

	private Bookmark bookmarkControl;
	
	// language spinner
	private Spinner labelSpinner;
	private List<LabelDto> labelList = new ArrayList<LabelDto>();
	private int selectedLabelNo = 0;
	private ArrayAdapter<LabelDto> labelArrayAdapter; 
	
	// the document list
	private List<BookmarkDto> bookmarkList = new ArrayList<BookmarkDto>();

	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_2;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, false);
        setContentView(R.layout.bookmarks);
        setIntegrateWithHistoryManager(true);

        bookmarkControl = ControlFactory.getInstance().getBookmarkControl();
        
        // if coming Back using History then the LabelNo will be in the intent allowing the correct label to be pre-selected
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			if (extras.containsKey(LABEL_NO_EXTRA)) {
				int labelNo = extras.getInt(LABEL_NO_EXTRA);
				if (labelNo>=0) {
					selectedLabelNo = labelNo;
				}
			}
		}
        
       	initialiseView();
    }

    private void initialiseView() {
    	
    	//prepare the Label spinner
    	loadLabelList();
    	labelArrayAdapter = new ArrayAdapter<LabelDto>(this, android.R.layout.simple_spinner_item, labelList);
    	labelArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	labelSpinner = (Spinner)findViewById(R.id.labelSpinner);
    	labelSpinner.setAdapter(labelArrayAdapter);

    	// check for pre-selected label e.g. when returning via History using Back button 
    	if (selectedLabelNo>=0 && selectedLabelNo<labelList.size()) {
    		labelSpinner.setSelection(selectedLabelNo);
    	}

    	labelSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
		    	selectedLabelNo = position;
		    	Bookmarks.this.loadBookmarkList();
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
    	
    	loadBookmarkList();
    	
    	// prepare the document list view
    	ArrayAdapter<BookmarkDto> bookmarkArrayAdapter = new BookmarkItemAdapter(this, LIST_ITEM_TYPE, bookmarkList);
    	setListAdapter(bookmarkArrayAdapter);
    	
    	registerForContextMenu(getListView());
    }

    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
    		bookmarkSelected(bookmarkList.get(position));
    	} catch (Exception e) {
    		Log.e(TAG, "document selection error", e);
    		showErrorMsg(R.string.error_occurred);
    	}
	}

    @Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.bookmark_context_menu, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		super.onContextItemSelected(item);
        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
		BookmarkDto bookmark = bookmarkList.get(menuInfo.position);
		if (bookmark!=null) {
			switch (item.getItemId()) {
			case (R.id.assign_labels):
				assignLabels(bookmark);
				return true;
			case (R.id.delete):
				delete(bookmark);
				return true;
			}
		}
		return false; 
	}

    @Override 
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(TAG, "Restoring state after return from label editing");
    	// the bookmarkLabels activity may have added/deleted labels or changed the bookmarks with the current label
    	LabelDto prevLabel = labelList.get(selectedLabelNo);
    	
    	// reload labels
    	loadLabelList();
    	
    	int prevLabelPos = labelList.indexOf(prevLabel);
    	if (prevLabelPos>=0) {
    		selectedLabelNo = prevLabelPos;
    	} else {
    		// this should be 'All'
    		selectedLabelNo = 0;
    	}
    	labelSpinner.setSelection(selectedLabelNo);
    	
    	// the label may have been renamed so cause the list to update it's text
    	labelArrayAdapter.notifyDataSetChanged();
    	
    	loadBookmarkList();
    }

    /** allow activity to enhance intent to correctly restore state */
	public Intent getIntentForHistoryList() {
		Log.d(TAG, "Saving label no in History Intent");
		Intent intent = getIntent();
		
		intent.putExtra(LABEL_NO_EXTRA, selectedLabelNo);

		return intent;
	}

    
    private void assignLabels(BookmarkDto bookmark) {
		Intent intent = new Intent(this, BookmarkLabels.class);
		intent.putExtra(BOOKMARK_ID_EXTRA, bookmark.getId());
		startActivityForResult(intent, 1);
	}

	private void delete(BookmarkDto bookmark) {
		bookmarkControl.deleteBookmark(bookmark);
		loadBookmarkList();
	}
    
	private void loadLabelList() {
    	labelList.clear();
    	labelList.addAll(bookmarkControl.getAllLabels());
	}

	/** a spinner has changed so refilter the doc list
     */
    private void loadBookmarkList() {
    	try {
    		if (selectedLabelNo>-1 && selectedLabelNo<labelList.size()) {
   	        	Log.i(TAG, "filtering bookmarks");
   	        	LabelDto selectedLabel = labelList.get(selectedLabelNo);
   	        	bookmarkList.clear();
   	        	bookmarkList.addAll( bookmarkControl.getBookmarksWithLabel(selectedLabel) );
   	        	
        		notifyDataSetChanged();
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "Error initialising view", e);
    		Toast.makeText(this, getString(R.string.error)+" "+e.getMessage(), Toast.LENGTH_SHORT).show();
    	}
    }

    /** user selected a document so download it
     * 
     * @param document
     */
    private void bookmarkSelected(BookmarkDto bookmark) {
    	Log.d(TAG, "Bookmark selected:"+bookmark.getVerse());
    	try {
        	if (bookmark!=null) {
        		ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().setKey(bookmark.getVerse());
        		doFinish();
        	}
    	} catch (Exception e) {
    		Log.e(TAG, "Error on attempt to download", e);
    		Toast.makeText(this, R.string.error_downloading, Toast.LENGTH_SHORT).show();
    	}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.bookmark_mynote_sort_menu, menu);
        return true;
    }

	/** 
     * on Click handlers
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean isHandled = false;
        
        switch (item.getItemId()) {
        // Change sort order
		case (R.id.sortByToggle):
			isHandled = true;
	    	try {
	    		bookmarkControl.changeBookmarkSortOrder();
	    		String sortDesc = bookmarkControl.getBookmarkSortOrderDescription();
				Toast.makeText(this, sortDesc, Toast.LENGTH_SHORT).show();
	    		loadBookmarkList();
	        } catch (Exception e) {
	        	Log.e(TAG, "Error sorting bookmarks", e);
	        	Dialogs.getInstance().showErrorMsg(R.string.error_occurred);
	        }

			break;
        }
        
		if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item);
        }
        
     	return isHandled;
    }

    private void doFinish() {
    	Intent resultIntent = new Intent();
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
}
