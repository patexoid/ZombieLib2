package com.patex.opds;

import com.patex.entities.Author;
import com.patex.entities.Book;
import com.rometools.rome.feed.atom.Entry;
import com.rometools.rome.feed.atom.Feed;
import com.rometools.rome.feed.atom.Person;
import com.rometools.rome.feed.synd.SyndPerson;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.view.feed.AbstractAtomFeedView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Service(OpdsView.OPDS_VIEW)
public class OpdsView extends AbstractAtomFeedView {

  public static final String OPDS_VIEW = "opdsView";
  public static final String TITLE = "Title";
  public static final String ENTRIES = "Entries";

  @Override
  protected List<Entry> buildFeedEntries(Map<String, Object> model, HttpServletRequest request, HttpServletResponse response) throws Exception {

    return (List<Entry>) model.get(ENTRIES);
  }

  @Override
  protected void buildFeedMetadata(Map<String, Object> model, Feed feed, HttpServletRequest request) {
    super.buildFeedMetadata(model, feed, request);
    feed.setTitle((String) model.get(TITLE));
    }
}
