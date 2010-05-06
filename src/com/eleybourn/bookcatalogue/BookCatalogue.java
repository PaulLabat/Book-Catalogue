/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

//import android.R;
import java.util.ArrayList;
import java.util.Iterator;

import android.app.AlertDialog;
import android.app.ExpandableListActivity;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.SimpleCursorTreeAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.ExpandableListView.OnGroupClickListener;
import android.widget.ExpandableListView.OnGroupCollapseListener;

/*
 * A book catalogue application that integrates with Google Books.
 */
public class BookCatalogue extends ExpandableListActivity {
    private static final int ACTIVITY_CREATE=0;
    private static final int ACTIVITY_EDIT=1;
    private static final int ACTIVITY_SORT=2;
    private static final int ACTIVITY_ISBN=3;
    private static final int ACTIVITY_SCAN=4;
    private static final int ACTIVITY_ADMIN=5;

    private CatalogueDBAdapter mDbHelper;
    private int mGroupIdColumnIndex; 
    private static final int SORT_BY_AUTHOR_EXPANDED = Menu.FIRST + 1; 
    private static final int SORT_BY_AUTHOR_COLLAPSED = Menu.FIRST + 2;
    private static final int SORT_BY_TITLE = Menu.FIRST + 3; 
    private static final int INSERT_ID = Menu.FIRST + 4;
    private static final int INSERT_ISBN_ID = Menu.FIRST + 5;
    private static final int INSERT_BARCODE_ID = Menu.FIRST + 6;
    private static final int DELETE_ID = Menu.FIRST + 7;
    private static final int SORT_BY_AUTHOR = Menu.FIRST + 8;
    private static final int ADMIN = Menu.FIRST + 9;

    public String bookshelf = "";
    private ArrayAdapter<String> spinnerAdapter;
    
    public int sort = 0;
    public int numAuthors = 0;
    private ArrayList<Integer> currentGroup = new ArrayList<Integer>();
    private boolean expanded = false;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	try {
			super.onCreate(savedInstanceState);
			setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
			setContentView(R.layout.list_authors);
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();
            bookshelf = getString(R.string.all_books);
			bookshelf();
			//fillData();
			registerForContextMenu(getExpandableListView());
    	} catch (Exception e) {
    		//Log.e("Book Catalogue", "Unknown Exception - BC onCreate - " + e.getMessage() );
    	}
    }

    
    private void bookshelf() {
        // Setup the Bookshelf Spinner 
    	Spinner mBookshelfText = (Spinner) findViewById(R.id.bookshelf_name);
    	spinnerAdapter = new ArrayAdapter<String>(this, R.layout.spinner_frontpage);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBookshelfText.setAdapter(spinnerAdapter);

        /* Add the default All Books bookshelf */
        spinnerAdapter.add(getString(R.string.all_books)); 
        
        Cursor bookshelves = mDbHelper.fetchAllBookshelves();
        if (bookshelves.moveToFirst()) { 
            do { 
            	spinnerAdapter.add(bookshelves.getString(1)); 
            } 
            while (bookshelves.moveToNext()); 
        } 
    	
        mBookshelfText.setOnItemSelectedListener(new OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> parentView, View view, int position, long id) {
				bookshelf = spinnerAdapter.getItem(position);
				fillData();
			}

			public void onNothingSelected(AdapterView<?> parentView) {
				// Do Nothing
				
			}
        });

    }
    
    private void fillData() {
    	if (sort == 1) {
    		fillDataTitle();
    	} else {
    		fillDataAuthor();
    	}
    	gotoCurrentGroup();
    }
    
    private void fillDataAuthor() {
    	Intent intent = getIntent();
    	// base the layout and the query on the sort order
        int layout = R.layout.row_authors;
        int layout_child = R.layout.row_authors_books;
    	
    	// Get all of the rows from the database and create the item list
        Cursor BooksCursor = null;
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			String query = intent.getStringExtra(SearchManager.QUERY);
			BooksCursor = mDbHelper.searchAuthors(query, bookshelf);
	    	numAuthors = BooksCursor.getCount();
	    	Toast.makeText(this, numAuthors + " " + this.getResources().getString(R.string.results_found), Toast.LENGTH_LONG).show();
			this.setTitle(R.string.search_title);
		} else {
			BooksCursor = mDbHelper.fetchAllAuthors(bookshelf);
	    	numAuthors = BooksCursor.getCount();
			this.setTitle(R.string.app_name);
		}
    	mGroupIdColumnIndex = BooksCursor.getColumnIndexOrThrow("_id");
        startManagingCursor(BooksCursor);
     
        // Create an array to specify the fields we want to display in the list
        String[] from = new String[]{CatalogueDBAdapter.KEY_FAMILY_NAME, CatalogueDBAdapter.KEY_GIVEN_NAMES};
        String[] exp_from = new String[]{CatalogueDBAdapter.KEY_TITLE, CatalogueDBAdapter.KEY_SERIES, CatalogueDBAdapter.KEY_SERIES_NUM};
        
        // and an array of the fields we want to bind those fields to (in this case just text1)
        int[] to = new int[]{R.id.row_family, R.id.row_given};
        int[] exp_to = new int[]{R.id.row_title, R.id.row_series, R.id.row_series_num};

        ExpandableListAdapter books = new AuthorBookListAdapter(BooksCursor, this, layout, layout_child, from, to, exp_from, exp_to);
        
        /* Handle the click event. Do not open, but goto the book edit page */
		ExpandableListView expandableList = getExpandableListView();
		expandableList.setOnGroupClickListener(new OnGroupClickListener() {
			public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
				addToCurrentGroup(groupPosition);
				return false;
			}
		});
		expandableList.setOnGroupCollapseListener(new OnGroupCollapseListener() {
			@Override
			public void onGroupCollapse(int groupPosition) {
				addToCurrentGroup(groupPosition);
				
			}
		});

		/* Hide the default expandable icon */
		Drawable indicator = this.getResources().getDrawable(R.drawable.expander_group); 
		expandableList.setGroupIndicator(indicator);
        
        setListAdapter(books);
    }
    
    private void fillDataTitle() {
    	Intent intent = getIntent();
    	// base the layout and the query on the sort order
        int layout = R.layout.row_books;
        int layout_child = R.layout.row_books;
    	
    	// Get all of the rows from the database and create the item list
        Cursor BooksCursor = null;
       	String order = CatalogueDBAdapter.KEY_TITLE + ", " + CatalogueDBAdapter.KEY_FAMILY_NAME;
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			String query = intent.getStringExtra(SearchManager.QUERY);
			BooksCursor = mDbHelper.searchBooks(query, order, bookshelf);
	    	numAuthors = BooksCursor.getCount();
	    	Toast.makeText(this, numAuthors + " " + this.getResources().getString(R.string.results_found), Toast.LENGTH_LONG).show();
			this.setTitle(R.string.search_title);
		} else {
			BooksCursor = mDbHelper.fetchAllBooks(order, bookshelf);
	    	numAuthors = BooksCursor.getCount();
			this.setTitle(R.string.app_name);
		}
    	mGroupIdColumnIndex = BooksCursor.getColumnIndexOrThrow("_id");
        startManagingCursor(BooksCursor);
     
        // Create an array to specify the fields we want to display in the list
        String[] from = new String[]{CatalogueDBAdapter.KEY_AUTHOR, CatalogueDBAdapter.KEY_TITLE, CatalogueDBAdapter.KEY_PUBLISHER, CatalogueDBAdapter.KEY_SERIES, CatalogueDBAdapter.KEY_SERIES_NUM};
        String[] exp_from = new String[]{};
        
        // and an array of the fields we want to bind those fields to (in this case just text1)
        int[] to = new int[]{R.id.row_author, R.id.row_title, R.id.row_publisher, R.id.row_series, R.id.row_series_num};
        int[] exp_to = new int[]{};

        ExpandableListAdapter books = new BooksBookListAdapter(BooksCursor, this, layout, layout_child, from, to, exp_from, exp_to);
        
        /* Handle the click event. Do not open, but goto the book edit page */
		ExpandableListView expandableList = getExpandableListView();
		/* Hack. So we can pass the current context into the onGroupClick event */ 
		final BookCatalogue pthis = this;
		expandableList.setOnGroupClickListener(new OnGroupClickListener() {
			public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
				addToCurrentGroup(groupPosition);
		        Intent i = new Intent(pthis, BookEdit.class);
		        i.putExtra(CatalogueDBAdapter.KEY_ROWID, id);
		        startActivityForResult(i, ACTIVITY_EDIT);
				return true;
			}
		});
		expandableList.setOnGroupCollapseListener(new OnGroupCollapseListener() {
			@Override
			public void onGroupCollapse(int groupPosition) {
				addToCurrentGroup(groupPosition);
				
			}
		});

		/* Hide the default expandable icon */
		Drawable indicator = new BitmapDrawable();
		indicator.setVisible(false, true);
		expandableList.setGroupIndicator(indicator);
        setListAdapter(books);
    }
    
    public class AuthorBookListAdapter extends SimpleCursorTreeAdapter {

    	boolean series = false;
    	
        public AuthorBookListAdapter(Cursor cursor, Context context, int groupLayout, int childLayout, String[] groupFrom, int[] groupTo, String[] childrenFrom, int[] childrenTo) {
            super(context, cursor, groupLayout, groupFrom, groupTo, childLayout, childrenFrom, childrenTo);
        }

        @Override
        protected Cursor getChildrenCursor(Cursor groupCursor) {
        	return mDbHelper.fetchAllBooksByAuthor(groupCursor.getInt(mGroupIdColumnIndex), bookshelf);
        }

        @Override
        public void setViewText(TextView v, String text) {
        	if (v.getId() == R.id.row_series) {
        		if (text.equals("")) {
        			series = false;
        		} else {
        			series = true;
        			text = "(" + text;
        		}
        	} else if (v.getId() == R.id.row_series_num) {
        		if (series == false) {
        			text = "";
        		} else if (series == true) {
            		if (text.equals("")) {
            			text = ")";
            		} else {
            			text = " #" + text + ")";
            		}
        		}
            	
        	} else if (v.getId() == R.id.row_family) {
        		text = text + ", ";
        	}
    		v.setText(text);
        }

    }
    
    public class BooksBookListAdapter extends SimpleCursorTreeAdapter {

    	boolean series = false;
    	
        public BooksBookListAdapter(Cursor cursor, Context context, int groupLayout, int childLayout, String[] groupFrom, int[] groupTo, String[] childrenFrom, int[] childrenTo) {
            super(context, cursor, groupLayout, groupFrom, groupTo, childLayout, childrenFrom, childrenTo);
        }

        @Override
        public void setViewText(TextView v, String text) {
        	if (v.getId() == R.id.row_series) {
        		if (text.equals("")) {
        			series = false;
        		} else {
        			series = true;
        			text = "" + text;
        		}
        	} else if (v.getId() == R.id.row_series_num) {
        		if (series == false) {
        			text = "";
        		} else if (series == true) {
            		if (text.equals("")) {
            			text = ")";
            		} else {
            			text = " #" + text + "";
            		}
        		}
        	}
    		v.setText(text);
        }

		@Override
		protected Cursor getChildrenCursor(Cursor groupCursor) {
			return null;
		}

    }
   
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        MenuItem insert = menu.add(0, INSERT_ID, 0, R.string.menu_insert);
        insert.setIcon(android.R.drawable.ic_menu_add);

        MenuItem insertBC = menu.add(0, INSERT_BARCODE_ID, 1, R.string.menu_insert_barcode);
        insertBC.setIcon(R.drawable.ic_menu_insert_barcode);

        MenuItem insertISBN = menu.add(0, INSERT_ISBN_ID, 2, R.string.menu_insert_isbn);
        insertISBN.setIcon(android.R.drawable.ic_menu_zoom);

        if (sort == 0) {
        	if (expanded == true) {
        		MenuItem collapse = menu.add(0, SORT_BY_AUTHOR_EXPANDED, 3, R.string.menu_sort_by_author_collapsed);
        		collapse.setIcon(R.drawable.ic_menu_collapse);
        	} else {
        		MenuItem expand = menu.add(0, SORT_BY_AUTHOR_COLLAPSED, 3, R.string.menu_sort_by_author_expanded);
        		expand.setIcon(R.drawable.ic_menu_expand);
        	}
        }

        if (sort == 0) {
        	MenuItem title = menu.add(0, SORT_BY_TITLE, 4, R.string.menu_sort_by_title);
        	title.setIcon(android.R.drawable.ic_menu_sort_alphabetically);
        } else {
        	MenuItem title = menu.add(0, SORT_BY_AUTHOR, 4, R.string.menu_sort_by_author);
        	title.setIcon(android.R.drawable.ic_menu_sort_alphabetically);
        }
        
        MenuItem admin = menu.add(0, ADMIN, 5, R.string.menu_administration);
        admin.setIcon(android.R.drawable.ic_menu_manage);
        
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch(item.getItemId()) {
        case SORT_BY_AUTHOR_COLLAPSED:
        	expandAll();
        	return true;
        case SORT_BY_AUTHOR_EXPANDED:
        	collapseAll();
            return true;
        case SORT_BY_TITLE:
        	sortByTitle();
            return true;
        case SORT_BY_AUTHOR:
        	sortByAuthor();
            return true;
        case INSERT_ID:
            //createDemoBook();
            //fillData();
            createBook();
            return true;
        case INSERT_ISBN_ID:
            createBookISBN();
        	return true;
        case INSERT_BARCODE_ID:
        	createBookScan();
            return true;
        case ADMIN:
            adminPage();
            return true;
        }

        return super.onMenuItemSelected(featureId, item);
    }
    
    /*
     * Expand and scroll to the current group
     */
    public void gotoCurrentGroup() {
		ExpandableListView view = this.getExpandableListView();
    	Iterator<Integer> arrayIterator = currentGroup.iterator();
		while(arrayIterator.hasNext()) {
			view.expandGroup(arrayIterator.next());
		}

    	try {
    		view.setSelectedGroup(currentGroup.get(currentGroup.size()-1));
    	} catch (Exception e) {
    		//do nothing
    	}
    	
    	return;
    }
    
    /*
     * add / remove items from the current group arraylist
     */
    public void addToCurrentGroup(int pos) {
    	addToCurrentGroup(pos, false);
    }
    
    /*
     * add / remove items from the current group arraylist
     */
    public void addToCurrentGroup(int pos, boolean force) {
    	int index = currentGroup.indexOf(pos);
    	if (index == -1) {
    		//it does not exist (so is not open), so add to the list
    		currentGroup.add(pos);
    	} else {
    		//it does exist (so is open), so remove from the list
    		currentGroup.remove(index);
    		if (force == true) {
    			currentGroup.add(pos);
    		}
    	}
    }

    /*
     * Expand all Author Groups
     */
    public void expandAll() {
		ExpandableListView view = this.getExpandableListView();
    	currentGroup = new ArrayList<Integer>();
		int i = 0;
		while (i < numAuthors) {
	    	addToCurrentGroup(i);
			view.expandGroup(i);
			//if (!expand) {
			//	break;
			//}
			i++;
		}
		expanded = true;
    }
    
    /*
     * Collapse all Author Groups
     */
    public void collapseAll() {
    	// there is no current group anymore
		ExpandableListView view = this.getExpandableListView();
		int i = 0;
		while (i < numAuthors) {
			view.collapseGroup(i);
			//if (!expand) {
			//	break;
			//}
			i++;
		}
    	currentGroup = new ArrayList<Integer>();
		expanded = false;
    }
	
    @Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		ExpandableListView.ExpandableListContextMenuInfo info = (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;
		try {
			// Only delete titles, not authors
			if (ExpandableListView.getPackedPositionType(info.packedPosition) == 1) {
				MenuItem delete = menu.add(0, DELETE_ID, 0, R.string.menu_delete);
				delete.setIcon(android.R.drawable.ic_menu_delete);
			}
		} catch (NullPointerException e) {
			// do nothing
		}
	}

    @Override
	public boolean onContextItemSelected(MenuItem item) {
		switch(item.getItemId()) {
    	case DELETE_ID:
    		ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) item.getMenuInfo();
	        mDbHelper.deleteBook(info.id);
	        fillData();
	        return true;
		}
		return super.onContextItemSelected(item);
	}
	
    /*
     * Load the BookCatalogueTitle Activity
     * 
     * return void
     */
    private void sortByTitle() {
        //Intent i = new Intent(this, BookCatalogueTitle.class);
        //startActivityForResult(i, ACTIVITY_SORT);
    	sort = 1;
    	fillData();
    }
	
    /*
     * Load the BookCatalogueTitle Activity
     * 
     * return void
     */
    private void sortByAuthor() {
    	sort = 0;
    	fillData();
    }
	
    /*
     * Load the BookEdit Activity
     * 
     * return void
     */
    private void adminPage() {
        Intent i = new Intent(this, Administration.class);
        startActivityForResult(i, ACTIVITY_ADMIN);
    }
	
    /*
     * Load the BookEdit Activity
     * 
     * return void
     */
    private void createBook() {
        Intent i = new Intent(this, BookEdit.class);
        startActivityForResult(i, ACTIVITY_CREATE);
    }
	
    /*
     * Load the BookEdit Activity
     * 
     * return void
     */
    private void createBookISBN() {
        Intent i = new Intent(this, BookISBNSearch.class);
        startActivityForResult(i, ACTIVITY_ISBN);
    }
	
    /*
     * Load the BookEdit Activity
     * 
     * return void
     */
    private void createBookScan() {
        Intent intent = new Intent("com.google.zxing.client.android.SCAN");
        //intent.putExtra("SCAN_MODE", "EAN_13");
        try {
            startActivityForResult(intent, ACTIVITY_SCAN);
        } catch (ActivityNotFoundException e) {
        	// Verify - this can be a dangerous operation
        	BookCatalogue pthis = this;
        	AlertDialog alertDialog = new AlertDialog.Builder(pthis).setMessage(R.string.install_scan).create();
            alertDialog.setTitle(R.string.install_scan_title);
            alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
            alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
            	public void onClick(DialogInterface dialog, int which) {
                    Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=\"com.google.zxing.client.android\"")); 
                	startActivity(marketIntent);
            		return;
            	}
            }); 
            alertDialog.setButton2("Cancel", new DialogInterface.OnClickListener() {
            	public void onClick(DialogInterface dialog, int which) {
            		//do nothing
            		return;
            	}
            }); 
            alertDialog.show();
            return;
        }
    }
   
    @Override
    public boolean onChildClick(ExpandableListView l, View v, int position, int childPosition, long id) {
		boolean result = super.onChildClick(l, v, position, childPosition, id);
		addToCurrentGroup(position, true);
    	if (sort == 0) {
	        Intent i = new Intent(this, BookEdit.class);
	        i.putExtra(CatalogueDBAdapter.KEY_ROWID, id);
	        startActivityForResult(i, ACTIVITY_EDIT);
    	}
        return result;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        switch(requestCode) {
        case ACTIVITY_SCAN:
        	try {
	        	String contents = intent.getStringExtra("SCAN_RESULT");
	        	Toast.makeText(this, R.string.isbn_found, Toast.LENGTH_LONG).show();
	            Intent i = new Intent(this, BookISBNSearch.class);
	            i.putExtra("isbn", contents);
	            startActivityForResult(i, ACTIVITY_ISBN);
	        } catch (NullPointerException e) {
	        	// This is not a scan result, but a normal return
	            fillData();
	        }
	        break;
        case ACTIVITY_CREATE:
        case ACTIVITY_EDIT:
        case ACTIVITY_SORT:
        case ACTIVITY_ISBN:
        case ACTIVITY_ADMIN:
        	 fillData();
        	break;
        }
   }


}