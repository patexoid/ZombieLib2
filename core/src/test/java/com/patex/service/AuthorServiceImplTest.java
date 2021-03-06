package com.patex.service;

import com.patex.Application;
import com.patex.entities.AuthorEntity;
import com.patex.entities.AuthorRepository;
import com.patex.messaging.TelegramMessenger;
import com.patex.zombie.model.AggrResult;
import com.patex.zombie.service.StorageService;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = Application.class)
@RunWith(SpringRunner.class)
public class AuthorServiceImplTest {

    @MockBean
    StorageService storageService;
    @MockBean
    DirWatcherService dirWatcherService;
    @MockBean
    TelegramMessenger telegramMessenger;
    @MockBean
    RabbitDuplicateHandler rabbitDuplicateHandler;
    @Autowired
    private AuthorServiceImpl authorService;
    @Autowired
    private AuthorRepository controller;

    @After
    public void tearDown() throws Exception {
        controller.deleteAll();
    }

    @Test
    public void shouldReturnLatestAuthorsCount1() {
        controller.save(new AuthorEntity("abcd1"));
        List<AggrResult> authorsCount = authorService.getAuthorsCount("");
        assertEquals("abcd1",authorsCount.get(0).getPrefix());
        assertEquals(1,authorsCount.get(0).getResult());
    }
    @Test
    public void shouldReturnLatestAuthorsCount2() {
        controller.save(new AuthorEntity("abcd1"));
        controller.save(new AuthorEntity("abcd2"));
        controller.save(new AuthorEntity("abcd3"));
        controller.save(new AuthorEntity("abcdd4"));
        controller.save(new AuthorEntity("abcdd6"));
        controller.save(new AuthorEntity("abcdd7"));
        List<AggrResult> authorsCount = authorService.getAuthorsCount("");
        authorsCount.forEach(aggrResult -> System.out.println(aggrResult.getPrefix() + " " + aggrResult.getResult()));
        assertEquals("abcd1",authorsCount.get(0).getPrefix());
        assertEquals(1,authorsCount.get(0).getResult());
        assertEquals("abcd2",authorsCount.get(1).getPrefix());
        assertEquals(1,authorsCount.get(1).getResult());
        assertEquals("abcd3",authorsCount.get(2).getPrefix());
        assertEquals(1,authorsCount.get(2).getResult());
        assertEquals("abcdd",authorsCount.get(3).getPrefix());
        assertEquals(3,authorsCount.get(3).getResult());
    }
}
