package com.patex.controllers;

import com.patex.entities.*;
import com.patex.opds.*;
import com.patex.opds.converters.AuthorEntry;
import com.patex.opds.converters.BookEntry;
import com.patex.opds.converters.ExpandedAuthorEntries;
import com.patex.opds.converters.SequenceEntry;
import com.patex.opds.latest.LatestURIComponent;
import com.patex.opds.latest.SaveLatest;
import com.patex.service.AuthorService;
import com.patex.service.BookService;
import com.patex.service.SequenceService;
import com.patex.utils.LinkUtils;
import com.patex.utils.Res;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.patex.utils.LinkUtils.makeURL;


@Controller
@RequestMapping(OPDSController.PREFIX)
public class OPDSController {

    static final String PREFIX = "opds";
    static final String APPLICATION_ATOM_XML = "application/atom+xml;charset=UTF-8";
    private static final String AUTHORSINDEX = "authorsindex";
    private static final int EXPAND_FOR_AUTHORS_COUNT = 3;
    private static final Logger log = LoggerFactory.getLogger(OPDSController.class);
    private final List<RootProvider> rootEntriesProvider = new ArrayList<>();

    private final AuthorService authorService;

    private final BookService bookService;

    private final SequenceService sequenceService;

    private final LatestURIComponent latestURIComponent;


    public OPDSController(AuthorService authorService, BookService bookService, SequenceService sequenceService,
                          LatestURIComponent latestURIComponent) {
        this.authorService = authorService;
        this.bookService = bookService;
        this.sequenceService = sequenceService;
        this.latestURIComponent = latestURIComponent;
    }

    private static <E> ModelAndView createMav(Res title, E e, Function<E, List<OPDSEntry>> func, Instant updated) {
        ModelAndView mav = new ModelAndView();
        mav.setViewName(OpdsView.OPDS_VIEW);
        if (e != null) {
            List<OPDSEntry> entries = func.apply(e);
            mav.addObject(OpdsView.ENTRIES, entries);
            if (updated == null) {
                updated = entries.stream().map(OPDSEntry::getUpdated).filter(Objects::nonNull).
                        max(Instant::compareTo).orElse(Instant.now());
            }
        } else {
            log.warn("empty obj:" + title);
        }
        mav.addObject(OpdsView.OPDS_METADATA, new OPDSMetadata(title, title.getKey(), updated));
        return mav;
    }

    private static <E> ModelAndView createMav(Res title, E e, Function<E, List<OPDSEntry>> func) {
        return createMav(title, e, func, null);
    }

    static ModelAndView createMav(Res title, List<OPDSEntry> entries) {
        return createMav(title, entries, e -> e);
    }

    public static ModelAndView createMav(Res title, List<OPDSEntry> entries, Instant updated) {
        return createMav(title, entries, e -> e, updated);
    }

    public void addRootPrivider(RootProvider provider) {
        rootEntriesProvider.add(provider);
    }

    @RequestMapping(produces = APPLICATION_ATOM_XML)
    public ModelAndView getMain() {
        List<OPDSEntry> rootEntries = new ArrayList<>();
        rootEntries.add(OPDSEntry.builder("root:latest", "opds.latest").
                addLink(makeURL(PREFIX, "latest"), OPDSLink.OPDS_CATALOG).
                build());
        rootEntries.add(OPDSEntry.builder("root:newBooks", "opds.newBooks").
                addLink(makeURL(PREFIX, "newBooks"), OPDSLink.OPDS_CATALOG).
                build());
        rootEntries.add(OPDSEntry.builder("root:authors", "opds.all.authors").
                addLink(makeURL(PREFIX, AUTHORSINDEX), OPDSLink.OPDS_CATALOG).
                build());
        rootEntries.addAll(
                rootEntriesProvider.stream().
                        map(RootProvider::getRoot).
                        flatMap(Collection::stream).
                        collect(Collectors.toList()));
        return createMav(new Res("opds.catalog"), rootEntries);
    }

    @RequestMapping(value = AUTHORSINDEX, produces = APPLICATION_ATOM_XML)
    public ModelAndView getAuthorsIndex() {
        return getAuthorsIndex("");
    }

    @SaveLatest
    @RequestMapping(value = AUTHORSINDEX + "/{start}", produces = APPLICATION_ATOM_XML)
    public ModelAndView getAuthorsIndex(@PathVariable(value = "start") String start) {
        return createMav(new Res("opds.all.authors"), authorService.getAuthorsCount(start),
                aggrResults -> aggrResults.stream().
                        flatMap(this::expandAggrResult).
                        sorted(Comparator.comparing(OPDSEntry::getTitle)).
                        collect(Collectors.toList()));
    }


    private Stream<OPDSEntry> expandAggrResult(AggrResult aggr) {
        if (aggr.getResult() >= EXPAND_FOR_AUTHORS_COUNT) {
            return Stream.of(OPDSEntry.builder(aggr.getId(),"first.value", aggr.getId()).
                    addLink(makeURL("opds", AUTHORSINDEX, LinkUtils.encode(aggr.getId())))
                    .build());
        } else {
            return authorService.findByName(aggr.getId()).stream().map(AuthorEntry::new);
        }
    }

    @SaveLatest
    @RequestMapping(value = "author/{id}", produces = APPLICATION_ATOM_XML)
    public ModelAndView getAuthor(@PathVariable(value = "id") long id) {
        Author authors = authorService.getAuthor(id);
        return createMav(new Res("opds.author.books", authors.getName()), authors, author ->
                new ExpandedAuthorEntries(author).getEntries()
        );
    }

    @SaveLatest
    @RequestMapping(value = "author/{id}/alphabet", produces = APPLICATION_ATOM_XML)
    public ModelAndView getAuthorBookAlphabet(@PathVariable(value = "id") long id) {
        Author bookAuthor = authorService.getAuthor(id);
        return createMav(new Res("opds.author.books.alphabet", bookAuthor.getName()), bookAuthor, author ->
                author.getBooks().stream().
                        map(AuthorBook::getBook).
                        filter(book -> !book.isDuplicate()).
                        map(BookEntry::new).
                        collect(Collectors.toList()));
    }

    @SaveLatest
    @RequestMapping(value = "authorsequenceless/{id}", produces = APPLICATION_ATOM_XML)
    public ModelAndView getAuthorBookNoSequence(@PathVariable(value = "id") long id) {
        Author bookAuthor = authorService.getAuthor(id);
        return createMav(new Res("opds.author.books.sequenceless", bookAuthor.getName()), bookAuthor, author ->
                author.getBooksNoSequence().stream().
                        map(AuthorBook::getBook).
                        filter(book -> !book.isDuplicate()).
                        map(BookEntry::new).
                        collect(Collectors.toList()));
    }

    @SaveLatest
    @RequestMapping(value = "sequence/{id}", produces = APPLICATION_ATOM_XML)
    public ModelAndView getBookBySequence(@PathVariable(value = "id") long id) {
        Sequence sequence = sequenceService.getSequence(id);
        return createMav(new Res("opds.author.books.sequence", sequence.getName()), sequence, seq ->
                seq.getBookSequences().stream().
                        sorted(Comparator.comparing(BookSequence::getSeqOrder)).
                        map(BookSequence::getBook).
                        filter(book -> !book.isDuplicate()).
                        map(BookEntry::new).
                        collect(Collectors.toList())
        );
    }

    @SaveLatest
    @RequestMapping(value = "authorsequences/{id}", produces = APPLICATION_ATOM_XML)
    public ModelAndView getAuthorSequences(@PathVariable(value = "id") long id) {
        Author author = authorService.getAuthor(id);
        return createMav(new Res("opds.author.sequence", author.getName()), author,
                a -> a.getSequencesStream().map(SequenceEntry::new).
                        collect(Collectors.toList()));
    }

    @RequestMapping(value = "latest")
    public ModelAndView getLatest(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1.
        response.setHeader("Pragma", "no-cache"); // HTTP 1.0.
        response.setHeader("Expires", "0"); // Proxies.
        response.setHeader("Content-Type", APPLICATION_ATOM_XML);
        return latestURIComponent.getLatestForCurrentUser();
    }

    @RequestMapping(value = "newBooks")
    public ModelAndView getNewBooks(@RequestParam(required = false, defaultValue = "0", name = "page") int page,
                                    @RequestParam(required = false, defaultValue = "20", name = "pageSize") int pageSize) {
        Page<Book> bookPage = bookService.getNewBooks(PageRequest.of(page, pageSize));

        return createMav(new Res("opds.newBooks"), bookPage.getContent(), b -> b.stream().map(BookEntry::new).
                collect(Collectors.toList()));
    }
}
