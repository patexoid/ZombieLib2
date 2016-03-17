package com.patex.service;

import com.patex.entities.Author;
import com.patex.entities.AuthorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Created by potekhio on 15-Mar-16.
 */
@Service
public class AuthorService {

  @Autowired
  AuthorRepository authorRepository;

  public Author getAuthor(long id){
    return authorRepository.findOne(id);
  }

  public Page<Author> findByName(String name, Pageable pageable) {
    return authorRepository.findByNameStartingWithIgnoreCase(name, pageable);
  }
}
