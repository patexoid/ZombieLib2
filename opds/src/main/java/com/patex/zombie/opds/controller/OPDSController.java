package com.patex.zombie.opds.controller;

import com.patex.zombie.LinkUtils;
import com.patex.zombie.model.AggrResult;
import com.patex.zombie.model.Author;
import com.patex.zombie.model.Book;
import com.patex.zombie.model.Res;
import com.patex.zombie.model.Sequence;
import com.patex.zombie.model.SequenceBook;
import com.patex.zombie.opds.controller.latest.LatestURIComponent;
import com.patex.zombie.opds.controller.latest.SaveLatest;
import com.patex.zombie.opds.model.OPDSEntry;
import com.patex.zombie.opds.model.OPDSEntryImpl;
import com.patex.zombie.opds.model.OPDSLink;
import com.patex.zombie.opds.model.OPDSMetadata;
import com.patex.zombie.opds.model.OpdsView;
import com.patex.zombie.opds.model.converter.AuthorEntry;
import com.patex.zombie.opds.model.converter.BookEntry;
import com.patex.zombie.opds.model.converter.ExpandedAuthorEntries;
import com.patex.zombie.opds.model.converter.SequenceEntry;
import com.patex.zombie.service.AuthorService;
import com.patex.zombie.service.BookService;
import com.patex.zombie.service.SequenceService;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Controller
@RequestMapping(OPDSController.PREFIX)
public class OPDSController {

    public static final String AUTHOR_NAME_PREFIX = "prefix";
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

    public static <E> ModelAndView createMav(Res title, E e, Function<E, List<OPDSEntry>> func, Instant updated) {
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

    public static <E> ModelAndView createMav(Res title, E e, Function<E, List<OPDSEntry>> func) {
        return createMav(title, e, func, null);
    }

    public static ModelAndView createMav(Res title, List<OPDSEntry> entries) {
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
        rootEntries.add(new OPDSEntryImpl("root:latest", new Res("opds.latest"),
                new OPDSLink(LinkUtils.makeURL(PREFIX, "latest"), OPDSLink.OPDS_CATALOG)));
        rootEntries.add(new OPDSEntryImpl("root:newBooks", new Res("opds.newBooks"),
                new OPDSLink(LinkUtils.makeURL(PREFIX, "newBooks"), OPDSLink.OPDS_CATALOG)));

        rootEntries.add(new OPDSEntryImpl("root:authors", new Res("opds.all.authors"),
                new OPDSLink(LinkUtils.makeURL(PREFIX, AUTHORSINDEX), OPDSLink.OPDS_CATALOG)));
        for (RootProvider rootProvider : rootEntriesProvider) {
            rootEntries.addAll(rootProvider.getRoot());
        }
        return createMav(new Res("opds.catalog"), rootEntries);
    }

    @SaveLatest
    @RequestMapping(value = AUTHORSINDEX, produces = APPLICATION_ATOM_XML)
    public ModelAndView getAuthorsIndex(
            @RequestParam(required = false, defaultValue = "", name = AUTHOR_NAME_PREFIX) String prefix) {
        return createMav(new Res("opds.all.authors"), authorService.getAuthorsCount(LinkUtils.decode(prefix)),
                aggrResults -> aggrResults.stream().
                        flatMap(this::expandAggrResult).
                        sorted(Comparator.comparing(OPDSEntry::getTitle)).
                        collect(Collectors.toList()));
    }


    private Stream<OPDSEntry> expandAggrResult(AggrResult aggr) {
        if (aggr.getResult() >= EXPAND_FOR_AUTHORS_COUNT) {
            String link = LinkUtils.makeURL("opds", AUTHORSINDEX)
                    + "?" + AUTHOR_NAME_PREFIX + "=" + LinkUtils.encode(aggr.getPrefix());
            Res title = new Res("first.value", aggr.getPrefix());
            return Stream.of(new OPDSEntryImpl(aggr.getPrefix(), title, null, link));
        } else {
            return authorService.findByName(aggr.getPrefix()).stream().map(AuthorEntry::new);
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
                        filter(book -> !book.isDuplicate()).
                        map(BookEntry::new).
                        collect(Collectors.toList()));
    }

    @SaveLatest
    @RequestMapping(value = "sequence/{id}", produces = APPLICATION_ATOM_XML)
    public ModelAndView getBookBySequence(@PathVariable(value = "id") long id) {
        Sequence sequence = sequenceService.getSequence(id);
        return createMav(new Res("opds.author.books.sequence", sequence.getName()), sequence, seq ->
                seq.getBooks().stream().
                        sorted(Comparator.comparing(SequenceBook::getSeqOrder)).
                        map(SequenceBook::getBook).
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
                a -> a.getSequences().stream().map(SequenceEntry::new).
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
