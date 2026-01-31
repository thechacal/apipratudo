package com.apipratudo.helpdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "helpdesk.firestore")
public class HelpdeskStorageProperties {

  private String ticketsCollection = "helpdesk_tickets";
  private String messagesCollection = "helpdesk_messages";
  private String templatesCollection = "helpdesk_templates";

  public String getTicketsCollection() {
    return ticketsCollection;
  }

  public void setTicketsCollection(String ticketsCollection) {
    this.ticketsCollection = ticketsCollection;
  }

  public String getMessagesCollection() {
    return messagesCollection;
  }

  public void setMessagesCollection(String messagesCollection) {
    this.messagesCollection = messagesCollection;
  }

  public String getTemplatesCollection() {
    return templatesCollection;
  }

  public void setTemplatesCollection(String templatesCollection) {
    this.templatesCollection = templatesCollection;
  }
}
