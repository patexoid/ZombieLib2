package com.patex.service;

import com.patex.entities.AuthorBookEntity;
import com.patex.entities.AuthorEntity;
import com.patex.entities.AuthorRepository;
import com.patex.entities.BookEntity;
import com.patex.entities.BookRepository;
import com.patex.entities.BookSequenceEntity;
import com.patex.entities.SequenceEntity;
import com.patex.entities.SequenceRepository;
import com.patex.mapper.BookMapperImpl;
import com.patex.parser.BookInfo;
import com.patex.parser.ParserService;
import com.patex.zombie.model.Book;
import com.patex.zombie.model.BookAuthor;
import com.patex.zombie.model.BookImage;
import com.patex.zombie.model.BookSequence;
import com.patex.zombie.model.User;
import com.patex.zombie.service.BookService;
import com.patex.zombie.service.StorageService;
import com.patex.zombie.service.TransactionService;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.text.RandomStringGenerator;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationEventPublisher;

import javax.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static org.apache.commons.text.CharacterPredicates.*;
import static org.junit.Assert.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

/**
 * Created by Alexey on 15.07.2017.
 */
@RunWith(MockitoJUnitRunner.class)
public class BooksServiceTest {
    private static final String FILE_NAME = "fileName";
    private static final String FIRST_AUTHOR = "first author";
    private static final String SECOND_AUTHOR = "second author";
    private static final String FIRST_SEQUENCE = "first sequence";

    private final RandomStringGenerator rsg = new RandomStringGenerator.Builder()
            .withinRange('0', 'z')
            .filteredBy(LETTERS, DIGITS)
            .build();
    private BookRepository bookRepo;
    private SequenceServiceImpl sequenceService;
    private AuthorRepository authorRepo;
    private ParserService parserService;
    private StorageService fileStorage;
    private TransactionService transactionService;
    private ApplicationEventPublisher eventPublisher;
    private BookService bookService;
    private ByteArrayInputStream bookIS;
    private User user;
    private BookInfo bookInfo;
    private BookEntity book;
    private BookMapperImpl bookMapper;

    @Before
    public void setUp() {
        parserService = mock(ParserService.class);
        bookRepo = mock(BookRepository.class);
        sequenceService = mock(SequenceServiceImpl.class);
        authorRepo = mock(AuthorRepository.class);

        bookIS = new ByteArrayInputStream(new byte[0]);
        user = new User();
        bookInfo = new BookInfo();
        book = new BookEntity();
        book.setAuthors(Collections.singleton(new AuthorEntity(1L, FIRST_AUTHOR)));
        book.setSequences(Collections.singletonList(new BookSequenceEntity(1, new SequenceEntity(FIRST_SEQUENCE))));
        bookInfo.setBook(book);

        when(parserService.getBookInfo(eq(FILE_NAME), any())).thenReturn(bookInfo);
        when(bookRepo.findFirstByTitleAndChecksum(any(), any())).thenReturn(Optional.empty());
        when(bookRepo.save(any(BookEntity.class))).thenAnswer(i -> i.getArguments()[0]);
//        when(sequenceService.mergeSequences(any())).thenAnswer(i -> {
//            Collection sequences = (Collection) i.getArguments()[0];
//            if (sequences == null) {
//                return null;
//            } else {
//                return sequences.iterator().next();
//            }
//        });
        when(authorRepo.findFirstByNameIgnoreCase(any())).thenReturn(Optional.empty());

        fileStorage = mock(StorageService.class);
        transactionService = new TransactionService();
        eventPublisher = mock(ApplicationEventPublisher.class);
        bookMapper = new BookMapperImpl();
        bookService = new BookServiceImpl(bookRepo, mock(SequenceRepository.class), authorRepo, parserService,
                fileStorage, transactionService, eventPublisher, bookMapper, mock(EntityManager.class));
    }

    @Test
    public void verifyUploadBook() {
        Book result = bookService.uploadBook(FILE_NAME, bookIS, user);
        verify(bookRepo).save(this.book);
        verify(fileStorage).save(any(), eq(FILE_NAME));
        assertEquals(FILE_NAME, book.getFileName());
        assertEquals(result, bookMapper.toDto(book));
    }

    @Test
    public void verifyUploadBookWithSavedAuthor() {
        long authorID = 42;
        when(authorRepo.findFirstByNameIgnoreCase(FIRST_AUTHOR)).thenReturn(Optional.of(new AuthorEntity(authorID, FIRST_AUTHOR)));

        Book result = bookService.uploadBook(FILE_NAME, bookIS, user);

        BookAuthor resultAuthor = result.getAuthors().get(0);
        assertEquals(authorID, resultAuthor.getId().longValue());
    }


    @Test
    @Ignore
    public void verifyUploadBookWithSavedAuthorAndSequence() {
        long authorID = 42;
        long seqeunceId = 54;
        AuthorEntity savedAuthor = new AuthorEntity(authorID, FIRST_AUTHOR);
        BookEntity savedBook = new BookEntity();
        SequenceEntity sequence = new SequenceEntity(seqeunceId, FIRST_SEQUENCE);
        savedBook.setSequences(Collections.singletonList(new BookSequenceEntity(1, sequence, savedBook)));
        savedAuthor.setBooks(Collections.singletonList(new AuthorBookEntity(savedAuthor, savedBook)));
        when(authorRepo.findFirstByNameIgnoreCase(FIRST_AUTHOR)).thenReturn(Optional.of(savedAuthor));

        Book result = bookService.uploadBook(FILE_NAME, bookIS, user);

        BookAuthor resultAuthor = result.getAuthors().get(0);
        assertEquals(authorID, resultAuthor.getId().longValue());
        BookSequence resultSequnce = result.getSequences().get(0);
        assertEquals(seqeunceId, resultSequnce.getId().longValue());
    }


    @Test
    @Ignore
    public void verifyMergeSequenceDuringBookUpload() {

        long firstSavedSequenceId = 1L;
        long secondSavedSequenceId = 2L;
        long mergedSequenceId = 3L;
        AuthorEntity firstSavedAuthor = new AuthorEntity(FIRST_AUTHOR);
        BookEntity firstSavedBook = new BookEntity();
        SequenceEntity firstSequence = new SequenceEntity(firstSavedSequenceId, FIRST_SEQUENCE);
        firstSavedBook.setSequences(Collections.singletonList(new BookSequenceEntity(1, firstSequence, firstSavedBook)));
        firstSavedAuthor.setBooks(Collections.singletonList(new AuthorBookEntity(firstSavedAuthor, firstSavedBook)));
        when(authorRepo.findFirstByNameIgnoreCase(FIRST_AUTHOR)).thenReturn(Optional.of(firstSavedAuthor));

        AuthorEntity secondSavedAuthor = new AuthorEntity(SECOND_AUTHOR);
        BookEntity secondSavedBook = new BookEntity();
        SequenceEntity secondSequence = new SequenceEntity(secondSavedSequenceId, FIRST_SEQUENCE);
        secondSavedBook.setSequences(Collections.singletonList(new BookSequenceEntity(1, secondSequence, secondSavedBook)));
        secondSavedAuthor.setBooks(Collections.singletonList(new AuthorBookEntity(secondSavedAuthor, secondSavedBook)));
        when(authorRepo.findFirstByNameIgnoreCase(SECOND_AUTHOR)).thenReturn(Optional.of(secondSavedAuthor));

        book.setAuthors(Arrays.asList(new AuthorEntity(FIRST_AUTHOR), new AuthorEntity(SECOND_AUTHOR)));

//        when(sequenceService.mergeSequences(any())).thenReturn(new Sequence(mergedSequenceId, FIRST_SEQUENCE));

        Book result = bookService.uploadBook(FILE_NAME, bookIS, user);

        verify(sequenceService).mergeSequences(anyList());
        assertEquals(mergedSequenceId, result.getSequences().get(0).getId().longValue());
    }


    @Test
    public void verifyBookCoverSave() {
        BookImage bookImage = new BookImage();
        byte[] imageBytes = new byte[]{1, 2, 3, 4, 5, 6};
        bookImage.setImage(imageBytes);
        String extension = "extension";
        bookImage.setType("image/" + extension);
        bookInfo.setBookImage(bookImage);

        bookService.uploadBook(FILE_NAME, bookIS, user);
        verify(fileStorage).save(aryEq(imageBytes), eq("image"), eq(FILE_NAME + "." + extension));

    }


    @Test
    public void testSameBookUpload() {
        User user = new User();
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        String fileName = rsg.generate(10);
        String uploadedTitle = rsg.generate(10);
        String existedTitle = rsg.generate(10);
        BookInfo uploadedBookInfo = new BookInfo();
        BookEntity uploadedBook = new BookEntity();
        uploadedBook.setTitle(uploadedTitle);
        uploadedBookInfo.setBook(uploadedBook);
        when(parserService.getBookInfo(eq(fileName), any(InputStream.class))).thenReturn(uploadedBookInfo);
        BookEntity savedBook = new BookEntity();
        savedBook.setTitle(existedTitle);
        when(bookRepo.findFirstByTitleAndChecksum(eq(uploadedTitle), any())).thenReturn(Optional.of(savedBook));

        Book book = bookService.uploadBook(fileName, bais, user);
        assertEquals("should be saved book", existedTitle, book.getTitle());
    }

    @Test
    @Ignore
    public void testSavedAuthorReplace() {
        String newAuthorName = rsg.generate(10);
        String existedAuthorName = rsg.generate(10);
        String fileName = rsg.generate(10);
        BookEntity book = new BookEntity();
        AuthorBookEntity abWithNewAuthor = new AuthorBookEntity();
        AuthorEntity newAuthor = new AuthorEntity();
        newAuthor.setName(newAuthorName);
        abWithNewAuthor.setAuthor(newAuthor);
        AuthorBookEntity abWithExistedAuthor = new AuthorBookEntity();
        AuthorEntity existedAuthor = new AuthorEntity();
        existedAuthor.setName(existedAuthorName);
        abWithExistedAuthor.setAuthor(existedAuthor);
        book.setAuthorBooks(Arrays.asList(abWithNewAuthor, abWithExistedAuthor));
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        BookInfo bookInfo = new BookInfo();
        bookInfo.setBook(book);
        when(parserService.getBookInfo(eq(fileName), any())).thenReturn(bookInfo);
        AuthorEntity savedAuthor = new AuthorEntity();
        savedAuthor.setName(existedAuthorName);
        when(authorRepo.findFirstByNameIgnoreCase(existedAuthorName)).thenReturn(Optional.of(savedAuthor));

        Book saved = bookService.uploadBook(fileName, bais, new User());
        assertTrue(saved.getAuthors().get(0).getId().equals(newAuthor.getId()));
        assertTrue(saved.getAuthors().get(1).getId().equals(savedAuthor.getId()));
    }

    @Test
    @Ignore
    public void testSequenceReplace() {

        String sequenceName = rsg.generate(10);
        String existedAuthorName = rsg.generate(10);
        String fileName = rsg.generate(10);
        long savedSeqId = RandomUtils.nextLong();

        BookEntity book = new BookEntity();

        SequenceEntity newSequence = new SequenceEntity();
        newSequence.setName(sequenceName);
        book.setSequences(Collections.singletonList(new BookSequenceEntity(2, newSequence)));
        AuthorBookEntity abWithExistedAuthor = new AuthorBookEntity();
        AuthorEntity existedAuthor = new AuthorEntity();
        existedAuthor.setName(existedAuthorName);
        abWithExistedAuthor.setAuthor(existedAuthor);
        book.setAuthorBooks(Collections.singletonList(abWithExistedAuthor));
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        BookInfo bookInfo = new BookInfo();
        bookInfo.setBook(book);
        when(parserService.getBookInfo(eq(fileName), any())).thenReturn(bookInfo);
        AuthorEntity savedAuthor = new AuthorEntity();
        savedAuthor.setName(existedAuthorName);
        BookEntity savedBook = new BookEntity();
        SequenceEntity savedSequence = new SequenceEntity();
        savedSequence.setName(sequenceName);
        savedSequence.setId(savedSeqId);
        savedBook.setSequences(Collections.singletonList(new BookSequenceEntity(1, savedSequence, savedBook)));
        savedAuthor.getBooks().add(new AuthorBookEntity(savedAuthor, savedBook));
        when(authorRepo.findFirstByNameIgnoreCase(existedAuthorName)).thenReturn(Optional.of(savedAuthor));

        Book saved = bookService.uploadBook(fileName, bais, new User());
        assertEquals(saved.getSequences().get(0).getId(), savedSequence.getId());
    }
}