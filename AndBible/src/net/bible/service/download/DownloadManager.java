/**
 * Distribution License:
 * JSword is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License, version 2.1 as published by
 * the Free Software Foundation. This program is distributed in the hope
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The License is available on the internet at:
 *       http://www.gnu.org/copyleft/lgpl.html
 * or by writing to:
 *      Free Software Foundation, Inc.
 *      59 Temple Place - Suite 330
 *      Boston, MA 02111-1307, USA
 *
 * Copyright: 2008
 *     The copyright to this program is held by it's authors.
 *
 * ID: $Id: BookIndexer.java 1466 2007-07-02 02:48:09Z dmsmith $
 */
package net.bible.service.download;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.Logger;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.BookFilters;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.install.InstallException;
import org.crosswire.jsword.book.install.InstallManager;
import org.crosswire.jsword.book.install.Installer;

/**
 * Originally copied from BookInstaller it calls Sword routines related to installation and removal of books and indexes
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author DM Smith [dmsmith555 at yahoo dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class DownloadManager {

	public static final String REPOSITORY_KEY = "repository";

    private static final Logger log = new Logger(DownloadManager.class.getName());
	
    public DownloadManager() {
        installManager = new InstallManager();
    }

	/**
	 * @param filter
	 * @return
	 */
	public List<Book> getDownloadableBooks(BookFilter filter, String repo, boolean refresh) throws InstallException {

		List<Book> documents = new ArrayList<Book>();
		
		try {
	        // If we know the name of the installer we can get it directly
	        Installer installer = installManager.getInstaller(repo);
	        
	        if (installer==null) {
				log.error("Error getting installer for repo "+repo);
				Dialogs.getInstance().showErrorMsg(R.string.error_occurred);
				documents = Collections.emptyList();
	        } else {
		        // Now we can get the list of books
		    	log.debug("getting downloadable books");
		    	if (installer.getBooks().size()==0 || refresh) {
		    		//todo should warn user of implications of downloading book list e.g. from persecuted country
		    		log.warn("Reloading book list");
		    		installer.reloadBookList();
		    	}
		
		        // Get a list of all the available books
		        documents = installer.getBooks(filter); //$NON-NLS-1$
	        }
	
	        //free memory
	        if (installer!=null) {
	        	installer.close();
	        }
		} catch (Exception e) {
			// ignore error because some minor repos are unreliable
			log.error("Fatal error downloading books from "+repo);
		}
    	log.info("number of documents available:"+documents.size());
		return documents;
	}
	
    /**
     * Install a book, overwriting it if the book to be installed is newer.
     * 
     * @param repositoryName
     *            the name of the repository from which to get the book
     * @param book
     *            the book to get
     * @throws BookException
     * @throws InstallException
     */
    public void installBook(final String repositoryName, final Book book) throws BookException, InstallException {
        // Delete the book, if present
        // At the moment, JSword will not re-install. Later it will, if the
        // remote version is greater.
        String bookInitials = book.getInitials();
        Book installedBook = Books.installed().getBook(bookInitials);
        if (installedBook!= null) {
        	// installedBook may differ by case of initials if Xiphos so delete installedBook rather than book
            unregisterBook(installedBook);
        }

        // Now install it in the background
        final Thread worker = new Thread("BookDownloader") {
            /* (non-Javadoc)
             * @see java.lang.Thread#run()
             */
            @Override
            public void run() {
                try {
                    // An installer knows how to install books
                    Installer installer = installManager.getInstaller(repositoryName);

                    installer.install(book);
                } catch (InstallException ex) {
                    Dialogs.getInstance().showErrorMsg(R.string.error_occurred);
                }
            }
        };

        // this actually starts the thread off
        worker.start();
    }

    /**
     * Install a book, overwriting it if the book to be installed is newer.
     * 
     * @param repositoryName
     *            the name of the repository from which to get the book
     * @param book
     *            the book to get
     * @throws BookException
     * @throws InstallException
     */
    public void installIndex(String repositoryName, Book book) throws BookException, InstallException {
    	// An installer knows how to install indexes
        log.debug("installIndex");
        Installer installer = installManager.getInstaller(repositoryName);
    	IndexDownloadThread idt = new IndexDownloadThread();
    	idt.downloadIndex(installer, book);
    }

    /**
     * Unregister a book from Sword registry.
     * 
     * This used to delete the book but there is an mysterious bug in deletion (see below). 
     * 
     * @param book
     *            the book to delete
     * @throws BookException
     */
    public void unregisterBook(Book book) throws BookException {
    	// this just seems to work so leave it here
    	// I used to think that the next delete was better - what a mess
    	// see this for potential problem: http://stackoverflow.com/questions/20437626/file-exists-returns-false-for-existing-file-in-android
    	// does file.exists return an incorrect value?
    	// To see the problem, reverse the commented lines below, and try downloading 2 or more Bibles that are already installed
        Books.installed().removeBook(book);

        // Avoid deleting all dir and files because "Java is known not to delete files immediately, so mkdir may fail sometimes"
        // http://stackoverflow.com/questions/617414/create-a-temporary-directory-in-java
        //
        // Actually do the delete
        // This should be a call on installer.
        //book.getDriver().delete(book);
    }

    /**
     * Get a list of all known installers.
     * 
     * @return the list of installers
     */
    public Map<String, Installer> getInstallers() {
        // Ask the Install Manager for a map of all known remote repositories
        // sites
        return installManager.getInstallers();
    }

    /**
     * Get a list of all installed books.
     * 
     * @return the list of installed books
     */
    public static List<Book> getInstalledBooks() {
        return Books.installed().getBooks();
    }

    /**
     * Get a list of installed books by BookFilter.
     * 
     * @param filter
     *            The book filter
     * @see BookFilter
     * @see Books
     */
    public static List<Book> getInstalledBooks(BookFilter filter) {
        return Books.installed().getBooks(filter);
    }

    /**
     * Get a list of books by CustomFilter specification
     * 
     * @param filterSpec
     *            The filter string
     * @see BookFilters#getCustom(java.lang.String)
     * @see Books
     */
    public static List<Book> getInstalledBooks(String filterSpec) {
        return getInstalledBooks(BookFilters.getCustom(filterSpec));
    }

    /**
     * Get a particular installed book by initials.
     * 
     * @param bookInitials
     *            The book name to search for
     * @return The found book. Null otherwise.
     */
    public static Book getInstalledBook(String bookInitials) {
        return Books.installed().getBook(bookInitials);
    }

    /**
     * Get a list of all known books for an installer.
     * 
     * @param repositoryName
     * @return the list of books at that repository
     */
    public List<Book> getRepositoryBooks(String repositoryName) {
        return installManager.getInstaller(repositoryName).getBooks();
    }

    /**
     * Get a list of books in a repository by BookFilter.
     * 
     * @param filter
     *            The book filter
     * @see BookFilter
     * @see Books
     */
    public List<Book> getRepositoryBooks(String repositoryName, BookFilter filter) {
        return installManager.getInstaller(repositoryName).getBooks(filter);
    }

    /**
     * Get a list of books in a repository by CustomFilter specification
     * 
     * @param filterSpec
     *            The filter string
     * @see BookFilters#getCustom(java.lang.String)
     * @see Books
     */
    public List<Book> getRepositoryBooks(String repositoryName, String filterSpec) {
        return getRepositoryBooks(repositoryName, BookFilters.getCustom(filterSpec));
    }

    /**
     * Get a particular installed book by initials.
     * 
     * @param bookInitials
     *            The book name to search for
     * @return The found book. Null otherwise.
     */
    public Book getRepositoryBook(String repositoryName, String bookInitials) {
        return installManager.getInstaller(repositoryName).getBook(bookInitials);
    }

    /**
     * Reload the local cache for a remote repository.
     * 
     * @param repositoryName
     * @throws InstallException
     */
    public void reloadBookList(String repositoryName) throws InstallException {
        installManager.getInstaller(repositoryName).reloadBookList();
    }

    /**
     * Get a Book from the repository. Note this does not install it.
     * 
     * @param repositoryName
     *            the repository from which to get the book
     * @param bookName
     *            the name of the book to get
     * @return the Book
     */
    public Book getBook(String repositoryName, String bookName) {
        return installManager.getInstaller(repositoryName).getBook(bookName);
    }


    private InstallManager installManager;
}
