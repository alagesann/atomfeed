Atom
====

What is Atom?
------------

[Atom](http://en.wikipedia.org/wiki/Atom_(standard\)) is an XML-based syndication format which represents time-ordered series of events. In Atom terminology, each event is an *entry* and the series is a *feed*.

Both feeds and entries have metadata associated with them, for example a title and a unique identifier.

    <?xml version="1.0">
        <feed xmlns="http://www.w3.org/2005/Atom">
            <id>urn:uuid:ff31a040-75bc-11e2-bcfd-0800200c9a66</id>
            <title type="text">Recent notifications</title>
            <updated>2013-01-02T10:03:00Z</updated>
            <generator></generator>
            <link rel="self" href="http://example.com/notifications" />
            <link rel="prev" href="http://example.com/feeds/3" />
            <entry>
                <id>urn:uuid:eb3ee5a0-75be-11e2-bcfd-0800200c9a66</id>
                <title type="text">Document edited</title>
                <updated>2013-01-02T10:01:00Z</updated>
                <author>
                    <name>jsmith</name>
                </author>
                <link rel="self" href="http://example.com/entry/33" />
                <content type="application/vnd.example.document+xml">
                    <title>HR policies</title>
                    <link href="http://example.com/cms/doc/456" />
                </content>
            </entry>
            <entry>
                <id>urn:uuid:1d3334a0-75bd-11e2-bcfd-0800200c9a66</id>
                <title type="text">User created</title>
                <updated>2013-01-02T07:31:00Z</updated>
                <author>
                    <name>dgonzales</name>
                </author>
                <link rel="self" href="http://example.com/entry/32" />
                <content type="application/vnd.example.user+xml">
                    <username>jsmith</username>
                    <role>editor</role>
                    <email>jsmith@example.com</email>
                </content>
            </entry>
        </feed>

Atom is optimised and designed for RESTful systems that communicate over HTTP.

Atom is a general-purpose format that can be extended to fit a particular domain. By using a well-understood general-purpose format as a basis, we can reuse tools infrastructure and semantics. The Atom syndication format was formalised in [RFC 4287](http://www.ietf.org/rfc/rfc4287).

Event-driven systems
--------------------

Atom can be used to implement event-driven systems by adding an entry to the feed every time something happens that subscribers might be interested in. Subscribers find out about these events by polling the feed. When new subscribers arrive, they can simply also start polling the feed without any change needed to the service.

Depending on the nature of the event and the size of the resource that it applies to, the Atom entry could either include a snapshot of the new state of the resource, or simply link to the resource so that the client can issue a fresh GET.

Because Atom is based on polling, it increases latency, which might make it unsuitable for real time systems. However, using a RESTful polling approach decouples client and server, and provides scalability through the opportunity for heavy caching.

Paginating feeds
----------------

As Atom entries are intended to be available indefinitely, the feed of events can grow very large over time. This makes the feed too large for consumers to conveniently GET over a network.

The solution is to break up a single logical feed into many phyisical feeds. As Atom is designed to follow RESTful conventions, Atom does this by means of links.

The server breaks up the series of events into separate physical feeds and gives each of them their own URL. The server might choose to make each physical feed represent a period of time e.g. a day, or might divide the series of events evenly so that each feed has e.g. 100 entries. 

Similar to a doubly-linked list, each physical feed has *prev* and *next* links that can be followed to find the next feed in the chain.

In addition to the URLs for each physical feed, the server also exposes a URL that points to the current working feed. This URL is effectively an alias for the feed that is still under construction.

    <?xml version="1.0">
        <feed xmlns="http://www.w3.org/2005/Atom">
            <id>urn:uuid:ff31a040-75bc-11e2-bcfd-0800200c9a66</id>
            <title type="text">Recent notifications</title>
            <link rel="self" href="http://example.com/notifications" />
            <link rel="prev" href="http://example.com/feeds/3" />
            <entry>..</entry>
            <entry>..</entry>
            <entry>..</entry>
        </feed>

This is the published entry point to the feed. Consumers of the event feed will always be able to dereference http://example.com/notifications to get the most recent entries.

The server would most likely add HTTP headers to prevent consumers from caching it, as the resource changes every time a new entry comes along (though this could depend on the specific requirements of the system). 

Consumers who are interested in older entries can follow the "prev" link to the previous feed.

    <?xml version="1.0">
        <feed xmlns="http://www.w3.org/2005/Atom">
            <id>urn:uuid:ff31a040-75bc-11e2-bcfd-0800200c9a66</id>
            <title type="text">Notifications</title>
            <link rel="self" href="http://example.com/feeds/4" />
            <link rel="prev" href="http://example.com/feeds/3" />
            <entry>..</entry>
            <entry>..</entry>
            <entry>..</entry>
        </feed>

This resource is effectively equivalent to the feed entry point. It represents the most recent feed that has not yet been finished. 

    <?xml version="1.0">
        <feed xmlns="http://www.w3.org/2005/Atom">
            <id>urn:uuid:ff31a040-75bc-11e2-bcfd-0800200c9a66</id>
            <title type="text">Notifications</title>
            <link rel="self" href="http://example.com/feeds/3" />
            <link rel="next" href="http://example.com/feeds/4" />
            <link rel="prev" href="http://example.com/feeds/2" />
            <entry>..</entry>
            <entry>..</entry>
            <entry>..</entry>
        </feed>

This feed has been finished, and should not change. It can therefore have cache ehaders with a long time-to-live.

Conumers wishing to find older or newer entries than the ones in this feed can find them by following the "prev" and "next" links respectively. 

    <?xml version="1.0">
        <feed xmlns="http://www.w3.org/2005/Atom">
            <id>urn:uuid:ff31a040-75bc-11e2-bcfd-0800200c9a66</id>
            <title type="text">Notifications</title>
            <link rel="self" href="http://example.com/feeds/2" />
            <link rel="next" href="http://example.com/feeds/3" />
            <link rel="prev" href="http://example.com/feeds/1" />
            <entry>..</entry>
            <entry>..</entry>
            <entry>..</entry>
        </feed>

This is another finished feed, and can also be heavily cached. Finding older or newer entries is again a matter of following the "prev" or "next" links.

Consuming feeds
---------------

A consumer of an Atom feed keeps track of the unique identifier of the most recent entry it has processed. Because the entries are time ordered, and the only change is to add new entries onto the front of the feed, consumers can work backwards till they find the oldest entry they have not yet processed, and then work forwards through the feed processing each event in turn. 

For example, a consumer might know that it has most recently processed the entry with id "urn:uuid:fc374b00-75c7-11e2-bcfd-0800200c9a66".

This consumer wants to check if there are any more recent entries, so it issues a GET request on http://example.com/notifications, which is the published entry-point of the feed.

    <?xml version="1.0">
        <feed xmlns="http://www.w3.org/2005/Atom">
            <link rel="self" href="http://example.com/notifications" />
            <link rel="prev" href="http://example.com/feeds/3" />
            <entry>
                <id>urn:uuid:e2089090-75c7-11e2-bcfd-0800200c9a66</id>
            </entry>
            <entry>
                <id>urn:uuid:d765c950-75c7-11e2-bcfd-0800200c9a66</id>
            </entry>
        </feed>

The entry with id "urn:uuid:fc374b00-75c7-11e2-bcfd-0800200c9a66" is not present. This is because since the consumer last checked the feed, the server has added new entries to the feed, closing off an old physical feed and starting a new one in the process.

The consumer therefore issues a GET request for the previous feed, which has the URL "http://example.com/feeds/3".

    <?xml version="1.0">
        <feed xmlns="http://www.w3.org/2005/Atom">
            <link rel="self" href="http://example.com/feeds/3" />
            <link rel="next" href="http://example.com/feeds/4" />
            <link rel="prev" href="http://example.com/feeds/2" />
            <entry>
                <id>urn:uuid:f37a81d0-75c7-11e2-bcfd-0800200c9a66</id>
            </entry>
            <entry>
                <id>urn:uuid:fc374b00-75c7-11e2-bcfd-0800200c9a66</id>
            </entry>
        </feed>

This time, the consumer does find the entry it last processed. The consumer can now start to work its way back to the front of the feed, by processing the one new entry in feed 3, and then going through the entries in the recent feed.

As the consumer goes through the entries, it keeps updating its record of the most recent entry processed.

Notice that the service does not have to keep track of who the consumer are or where they are up to. The guarantee that new events are always added to the front of the list allows consumers to do that for themselves.

References
----------

A great reference for understanding Atom's use in RESTful event-driven systems is Chapter 7 of [REST in Practice](http://restinpractice.com/book/) by Jim Webber, Savas Parastatidis and Ian Robinson.