package com.patex.extlib;


import com.patex.LibException;
import com.patex.entities.Book;
import com.patex.entities.ExtLibrary;
import com.patex.entities.SavedBook;
import com.patex.entities.SavedBookRepository;
import com.patex.entities.ZUser;
import com.patex.opds.converters.OPDSAuthor;
import com.patex.opds.converters.OPDSEntryI;
import com.patex.opds.converters.OPDSLink;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.patex.extlib.ExtLibService.FB2_TYPE;
import static com.patex.extlib.ExtLibService.REL_NEXT;

@Service
public class ExtLibDownloadService {

    private static Logger log = LoggerFactory.getLogger(ExtLibService.class);

    private final ExecutorService executor = new DelegatingSecurityContextExecutorService(
            Executors.newCachedThreadPool(r -> {
                AtomicInteger count = new AtomicInteger();
                Thread thread = new Thread(r);
                thread.setName("ExtLibDownloadService-" + count.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }));


    private final ExtLibConnection connection;
    private final  ExtLibInScopeRunner scopeRunner;
    private final  SavedBookRepository savedBookRepo;

    @Autowired
    public ExtLibDownloadService(ExtLibConnection connection, ExtLibInScopeRunner scopeRunner, SavedBookRepository savedBookRepo) {
        this.connection = connection;
        this.scopeRunner = scopeRunner;
        this.savedBookRepo = savedBookRepo;
    }

    public Book downloadBook(ExtLibrary library, String uri, String type, ZUser user) {
        Book book = scopeRunner.runInScope(library, () -> downloadBook(uri, type, user));
        savedBookRepo.save(new SavedBook(library, uri));
        return book;
    }

    private Book downloadBook(String uri, String type, ZUser user) {
        return connection.downloadBook(uri, type, user);
    }

    public ExtLibFeed getExtLibFeed(ExtLibrary library, String uri) throws LibException {
        return scopeRunner.runInScope(library, () -> getExtLibFeed(uri));
    }

    private ExtLibFeed getExtLibFeed(String uri) {
        SyndFeed feed = connection.getFeed(uri);
        List<OPDSEntryI> entries = feed.getEntries().stream().map(ExtLibOPDSEntry::new).
                collect(Collectors.toList());

        List<OPDSLink> links = new ArrayList<>();
        Optional<SyndLink> nextPage = feed.getLinks().stream().
                filter(syndLink -> REL_NEXT.equals(syndLink.getRel())).findFirst();
        nextPage.ifPresent(syndLink -> links.add(ExtLibOPDSEntry.mapLink(syndLink)));
        return new ExtLibFeed(feed.getTitle(), entries, links);
    }


    public CompletableFuture<Optional<DownloadAllResult>> downloadAll(ExtLibrary library, String uri, ZUser user) {

        Supplier<Optional<DownloadAllResult>> supplier = () ->
                scopeRunner.runInScope(library, () -> downloadAll(uri, user, library));
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    private Optional<DownloadAllResult> downloadAll(String uri, ZUser user, ExtLibrary library) {
        List<OPDSEntryI> entries = getExtLibFeed(uri).getEntries();
        Set<String> saved = getAlreadySaved(library, entries);
        return entries.stream().
                filter(entry -> entry.getLinks().stream().map(OPDSLink::getHref).
                        map(ExtLibService::extractExtUri).
                        filter(Optional::isPresent).map(Optional::get).noneMatch(saved::contains)
                ).map(entry -> download(entry, user))
                .reduce(DownloadAllResult::concat);
    }

    private Set<String> getAlreadySaved(ExtLibrary library, List<OPDSEntryI> entries) {
        List<String> links = entries.stream().
                map(OPDSEntryI::getLinks).
                flatMap(Collection::stream).map(OPDSLink::getHref).
                map(ExtLibService::extractExtUri).filter(Optional::isPresent).map(Optional::get).
                distinct().collect(Collectors.toList());
        return savedBookRepo.findSavedBooksByExtLibraryAndExtIdIn(library, links).
                stream().map(SavedBook::getExtId).distinct().collect(Collectors.toSet());
    }

    private DownloadAllResult download(OPDSEntryI entry, ZUser user) {
        List<OPDSLink> links = entry.getLinks().stream().
                filter(link -> link.getType().contains(FB2_TYPE)).collect(Collectors.toList());
        List<String> authors = entry.getAuthors().orElse(Collections.emptyList()).stream().
                map(OPDSAuthor::getName).collect(Collectors.toList());
        if (links.size() == 0) {
            return DownloadAllResult.empty(authors, entry.getTitle());
        } else {
            if (links.size() > 1) {
                log.warn("Book id: " + entry.getId() + " have more than 1 download link " +
                        "\nBook title:" + entry.getTitle());
            }
            try {
                String uri = ExtLibService.extractExtUri(links.get(0).getHref()).orElse("");
                String type = "fb2";
                Book book = downloadBook(uri, type, user);
                return DownloadAllResult.success(authors, book);
            } catch (LibException e) {
                log.error(e.getMessage(), e);
                return DownloadAllResult.failed(authors, entry.getTitle());
            }
        }
    }
}