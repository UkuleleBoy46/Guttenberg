/*
 * Copyright (C) 2019 SOBotics
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.sobotics.guttenberg.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sobotics.chatexchange.chat.Message;
import org.sobotics.chatexchange.chat.Room;
import org.sobotics.guttenberg.clients.Guttenberg;
import org.sobotics.guttenberg.entities.Post;
import org.sobotics.guttenberg.entities.PostMatch;
import org.sobotics.guttenberg.finders.PlagFinder;
import org.sobotics.guttenberg.search.InternetSearch;
import org.sobotics.guttenberg.search.SearchItem;
import org.sobotics.guttenberg.search.SearchResult;
import org.sobotics.guttenberg.search.SearchTerms;
import org.sobotics.guttenberg.services.ApiService;
import org.sobotics.guttenberg.services.RunnerService;
import org.sobotics.guttenberg.utils.*;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Command to search for plagiarism using internet
 *
 * @author Petter Friberg
 */
public class CheckInternet implements SpecialCommand {

  protected static final String STACKOVERFLOW = "stackoverflow";
  protected static final String ITEMS = "items";
  private static final Logger LOGGER = LoggerFactory.getLogger(CheckInternet.class);
  private static final String CMD = "checkinternet";

  protected Message message;


  public CheckInternet(Message message) {
    this.message = message;

  }


  /**
   * Only for testing off chat
   *
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    CheckInternet cu = new CheckInternet(null);
    Properties prop = new Properties();

    try {
      prop = FileUtils.getPropertiesFromFile(FilePathUtils.loginPropertiesFile);
    } catch (IOException e) {
      LOGGER.error("Could not read login.properties", e);
      return;
    }

    Guttenberg.setLoginProperties(prop);

    int postId = 38717190;
    Post post = cu.getPost(postId, prop);
    if (post != null) {
      cu.checkPost(post, new SearchTerms(post));
    }

  }


  @Override
  public boolean validate() {
    return CommandUtils.checkForCommand(message.getPlainContent(), CMD);
  }


  @Override
  public void execute(Room room, RunnerService instance) {
    String cmd = message.getPlainContent();

    // Get userid
    int index = cmd.indexOf(CMD);
    if (index == -1) {
      LOGGER.warn("This command should not have been invoked with: " + message.getPlainContent());
      return;
    }

    Integer postId = PostUtils.getIdFromLink(cmd.substring(index, cmd.length()));

    if (postId == null) {
      room.replyTo(message.getId(), "Could not find answer id, check command syntax");
      return;
    }

    Properties prop = Guttenberg.getLoginProperties();
    Post post = null;

    LOGGER.info("Executing command on user id: " + postId);

    try {

      post = getPost(postId, prop);
      if (post == null) {
        room.replyTo(message.getId(), "Could not find post id: " + postId + " with api call");
        return;
      }
      SearchTerms st = new SearchTerms(post);
      output(room, PrintUtils.printDescription() + "*Checking post: [" + post.getAnswerID() + "](https://stackoverflow.com/a/" + post.getAnswerID() + ") Search term: " + st.getQuery() + ", exact match: " + st.getExactTerm() + "*");
      throttleForChat();
      SearchResult sr = checkPost(post, st);
      output(room, post, sr);

    } catch (IOException e) {
      LOGGER.error("Error calling API", e);
      room.replyTo(message.getId(), "Error calling search, maybe we ran out of quota");
    }

  }


  /**
   * Search internet for similar post and use gut to check'em
   *
   * @param room, the chat room
   * @param post, the post
   * @throws IOException
   */
  protected SearchResult checkPost(Post post, SearchTerms st) throws IOException {


    InternetSearch is = new InternetSearch();
    SearchResult result = is.google(post, st);

    if (result.getItems().isEmpty()) {
      return result;
    }

    List<Post> relatedAnswers = getRelatedAnswers(post, result);
    if (!relatedAnswers.isEmpty()) {
      PlagFinder finder = new PlagFinder(post, relatedAnswers);
      List<PostMatch> matches = finder.matchesForReasons(true);
      if (!matches.isEmpty()) {
        Collections.sort(matches);
        PostMatch bestMatch = matches.get(0);
        result.setPostMatch(bestMatch);
      }
    }

    return result;


  }


  protected void output(Room room, Post post, SearchResult result) {
    if (result == null || result.getItems().isEmpty()) {
      output(room, "No search results on search term");
      return;
    }


    SearchItem bestSOPost = result.getFirstResult(true);
    SearchItem bestOffSitePost = result.getFirstResult(false);

    StringBuilder searchMessage = new StringBuilder(PrintUtils.printDescription());
    if (bestSOPost != null) {
      searchMessage.append("On site: ");
      if (bestSOPost.isPost(post)) {
        searchMessage.append("[Same post]");
      } else {
        searchMessage.append("[").append(bestSOPost.getTitle()).append("]");
      }
      appendLinkAndPosition(result, bestSOPost, searchMessage);
    }
    if (searchMessage.length() > 0 && bestOffSitePost != null) {
      searchMessage.append(", Off-site: ");
    }
    if (bestOffSitePost != null) {
      searchMessage.append("[").append(bestOffSitePost.getTitle()).append("]");
      appendLinkAndPosition(result, bestOffSitePost, searchMessage);
    }

    PostMatch bestMatch = result.getPostMatch();
    if (bestMatch != null) {
      searchMessage.append(", SO Match: ");
      searchMessage.append("[").append(bestMatch.getOriginal().getAnswerID()).append("]");
      String originalLink = "https://stackoverflow.com/a/" + bestMatch.getOriginal().getAnswerID();
      searchMessage.append("(").append(originalLink).append(") Score:").append(NumberFormat.getNumberInstance().format(bestMatch.getTotalScore()));
    }
    output(room, searchMessage.toString());

  }


  protected void throttleForChat() {
    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
    }
  }


  /**
   * Get related answers, this could probably refractor to plag finder
   *
   * @param post,   the original post
   * @param result, the search result
   * @return List of related answers.
   * @throws IOException
   */
  public List<Post> getRelatedAnswers(Post post, SearchResult result) throws IOException {
    List<Post> relatedAnswers = new ArrayList<>();
    List<Integer> ids = result.getIdQuestions();

    if (!ids.isEmpty()) {
      String relatedIds = ids.stream().map(id -> String.valueOf(id)).collect(Collectors.joining(";"));
      JsonObject ra = ApiService.defaultService.getAnswersToQuestionsByIdString(relatedIds);
      for (JsonElement answer : ra.get("items").getAsJsonArray()) {
        JsonObject answerObject = answer.getAsJsonObject();
        Post answerPost = PostUtils.getPost(answerObject);
        if (answerPost.getAnswerID().intValue() != post.getAnswerID().intValue()) {
          relatedAnswers.add(answerPost);
        }
      }
    }
    return relatedAnswers;
  }


  public void appendLinkAndPosition(SearchResult result, SearchItem bestSOPost, StringBuilder searchMessage) {
    searchMessage.append("(").append(bestSOPost.getLink()).append(") [").append(result.getItems().indexOf(bestSOPost) + 1).append("]");
  }


  /**
   * Debug util to use even if not in room
   *
   * @param room
   * @param message
   */
  public void output(Room room, String message) {
    LOGGER.info("Sending message: " + message);
    if (room == null) {
      System.out.println(message);
    } else {
      room.send(message);
    }
  }


  /**
   * Load a Post from a post id (refrator to add to post util?
   *
   * @param postId, id post (it would be better if a Long)
   * @param prop,   properties (maybe they should be static application wide)
   * @return
   * @throws IOException
   */
  public Post getPost(int postId, Properties prop) throws IOException {
    Post post = null;
    JsonObject answer = ApiUtils.getAnswerDetailsById(postId, STACKOVERFLOW, prop.getProperty("apikey", ""));
    if (answer != null && answer.has(ITEMS)) {
      for (JsonElement element : answer.get(ITEMS).getAsJsonArray()) {
        JsonObject object = element.getAsJsonObject();
        post = PostUtils.getPost(object);
        break;
      }
    }
    return post;
  }


  @Override
  public String description() {
    return "Checks post for plagiarism using internet: checkinternet <answerId>";
  }


  @Override
  public String name() {
    return CMD;
  }


  @Override
  public boolean availableInStandby() {
    return false;
  }

}
