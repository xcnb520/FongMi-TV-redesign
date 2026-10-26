package com.fongmi.player.core.source.dash;

import com.fongmi.player.core.PlaybackException;
import com.fongmi.player.core.source.Segment;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

public final class DashParser {

    private static final Pattern NUMBER_FORMAT = Pattern.compile("\\$Number%0(\\d+)d\\$");
    private static final Pattern DURATION = Pattern.compile("P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:([\\d.]+)S)?)?");

    public static DashManifest parse(String xml, String manifestUrl) throws PlaybackException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new StringReader(xml)));
            Element root = document.getDocumentElement();
            String base = resolve(manifestUrl, childText(root, "BaseURL"));
            long durationUs = parseDuration(attr(root, "mediaPresentationDuration", ""));
            boolean dynamic = "dynamic".equals(attr(root, "type", "static"));
            DashManifest manifest = new DashManifest(base, durationUs, dynamic);
            for (Element period : children(root, "Period")) {
                String periodBase = resolve(base, childText(period, "BaseURL"));
                long periodDurationUs = parseDuration(attr(period, "duration", ""));
                if (periodDurationUs <= 0L) periodDurationUs = durationUs;
                for (Element adaptation : children(period, "AdaptationSet")) {
                    String adaptationBase = resolve(periodBase, childText(adaptation, "BaseURL"));
                    String adaptationMime = attr(adaptation, "mimeType", null);
                    String adaptationCodecs = attr(adaptation, "codecs", null);
                    String adaptationLanguage = attr(adaptation, "lang", null);
                    int adaptationWidth = attrInt(adaptation, "width", 0);
                    int adaptationHeight = attrInt(adaptation, "height", 0);
                    float adaptationFrameRate = parseFrameRate(attr(adaptation, "frameRate", ""));
                    Element adaptationTemplate = child(adaptation, "SegmentTemplate");
                    Element adaptationList = child(adaptation, "SegmentList");
                    for (Element representation : children(adaptation, "Representation")) {
                        String representationBase = resolve(adaptationBase, childText(representation, "BaseURL"));
                        String id = attr(representation, "id", "");
                        String mime = attr(representation, "mimeType", adaptationMime);
                        String codecs = attr(representation, "codecs", adaptationCodecs);
                        String language = attr(representation, "lang", adaptationLanguage);
                        long bandwidth = attrLong(representation, "bandwidth", 0L);
                        int width = attrInt(representation, "width", adaptationWidth);
                        int height = attrInt(representation, "height", adaptationHeight);
                        float frameRate = parseFrameRate(attr(representation, "frameRate", ""));
                        if (frameRate <= 0f) frameRate = adaptationFrameRate;
                        DashRepresentation result = new DashRepresentation(id, mime, codecs, language, bandwidth, width, height, frameRate, periodDurationUs);
                        Element template = child(representation, "SegmentTemplate");
                        if (template == null) template = adaptationTemplate;
                        Element list = child(representation, "SegmentList");
                        if (list == null) list = adaptationList;
                        if (template != null) {
                            parseTemplate(result, template, representationBase, id, bandwidth, periodDurationUs);
                        } else if (list != null) {
                            parseList(result, list, representationBase);
                        } else {
                            result.getSegments().add(new Segment(representationBase, 0L, -1L, periodDurationUs, 0L));
                        }
                        manifest.getRepresentations().add(result);
                    }
                }
            }
            return manifest;
        } catch (Exception e) {
            throw new PlaybackException(PlaybackException.SOURCE_UNSUPPORTED, e.getMessage(), e);
        }
    }

    private static void parseTemplate(DashRepresentation representation, Element template, String base, String id, long bandwidth, long periodDurationUs) {
        long timescale = attrLong(template, "timescale", 1L);
        long startNumber = attrLong(template, "startNumber", 1L);
        long duration = attrLong(template, "duration", 0L);
        long presentationTimeOffset = attrLong(template, "presentationTimeOffset", 0L);
        String initialization = attr(template, "initialization", null);
        String media = attr(template, "media", null);
        if (initialization != null) {
            String initUrl = resolve(base, expand(initialization, id, bandwidth, startNumber, 0L));
            representation.getSegments().add(new Segment(initUrl, 0L, -1L, 0L, 0L));
        }
        if (media == null) return;
        Element timeline = child(template, "SegmentTimeline");
        long number = startNumber;
        if (timeline != null) {
            long currentTime = -1L;
            for (Element s : children(timeline, "S")) {
                long time = attrLong(s, "t", currentTime);
                if (time < 0L) time = 0L;
                long segmentDuration = attrLong(s, "d", 0L);
                long repeat = attrLong(s, "r", 0L);
                if (repeat < 0L) repeat = 0L;
                for (long i = 0; i <= repeat; i++) {
                    String url = resolve(base, expand(media, id, bandwidth, number, time));
                    long startTimeUs = Math.max(0L, time - presentationTimeOffset) * 1_000_000L / timescale;
                    long durationUs = segmentDuration * 1_000_000L / timescale;
                    representation.getSegments().add(new Segment(url, 0L, -1L, durationUs, startTimeUs));
                    time += segmentDuration;
                    number++;
                }
                currentTime = time;
            }
        } else if (duration > 0L) {
            long count = periodDurationUs > 0L ? (periodDurationUs * timescale + duration - 1L) / duration : 1L;
            for (long i = 0; i < count; i++) {
                long time = i * duration;
                String url = resolve(base, expand(media, id, bandwidth, number, time));
                long startTimeUs = Math.max(0L, time - presentationTimeOffset) * 1_000_000L / timescale;
                long durationUs = duration * 1_000_000L / timescale;
                representation.getSegments().add(new Segment(url, 0L, -1L, durationUs, startTimeUs));
                number++;
            }
        }
    }

    private static void parseList(DashRepresentation representation, Element list, String base) {
        long timescale = attrLong(list, "timescale", 1L);
        long duration = attrLong(list, "duration", 0L);
        Element initialization = child(list, "Initialization");
        if (initialization != null) {
            String source = attr(initialization, "sourceURL", null);
            String sourceBase = resolve(base, source);
            long[] range = parseRange(attr(initialization, "range", ""));
            representation.getSegments().add(new Segment(sourceBase, range[0], range[1], 0L, 0L));
        }
        long startTimeUs = 0L;
        for (Element segmentUrl : children(list, "SegmentURL")) {
            String media = attr(segmentUrl, "media", null);
            if (media == null) continue;
            String url = resolve(base, media);
            long[] range = parseRange(attr(segmentUrl, "mediaRange", ""));
            long durationUs = duration > 0L ? duration * 1_000_000L / timescale : 0L;
            representation.getSegments().add(new Segment(url, range[0], range[1], durationUs, startTimeUs));
            startTimeUs += durationUs;
        }
    }

    private static String expand(String template, String id, long bandwidth, long number, long time) {
        String result = template
                .replace("$RepresentationID$", id == null ? "" : id)
                .replace("$Bandwidth$", Long.toString(bandwidth))
                .replace("$Time$", Long.toString(time));
        Matcher matcher = NUMBER_FORMAT.matcher(result);
        if (matcher.find()) {
            int width = Integer.parseInt(matcher.group(1));
            String formatted = String.format(LocaleHolder.US, "%0" + width + "d", number);
            result = matcher.replaceAll(Matcher.quoteReplacement(formatted));
        }
        return result.replace("$Number$", Long.toString(number));
    }

    private static String resolve(String base, String reference) {
        if (reference == null || reference.isEmpty()) return base;
        try {
            if (reference.startsWith("http://") || reference.startsWith("https://") || reference.startsWith("file:")) return reference;
            return new URL(new URL(base), reference).toString();
        } catch (Exception e) {
            return reference;
        }
    }

    private static long parseDuration(String value) {
        if (value == null || value.isEmpty()) return 0L;
        Matcher matcher = DURATION.matcher(value.trim());
        if (!matcher.find()) return 0L;
        long days = parseLong(matcher.group(1));
        long hours = parseLong(matcher.group(2));
        long minutes = parseLong(matcher.group(3));
        double seconds = parseDouble(matcher.group(4));
        return (days * 86400L + hours * 3600L + minutes * 60L) * 1_000_000L + (long) (seconds * 1_000_000d);
    }

    private static float parseFrameRate(String value) {
        if (value == null || value.isEmpty()) return 0f;
        try {
            int slash = value.indexOf('/');
            if (slash > 0) return Float.parseFloat(value.substring(0, slash)) / Float.parseFloat(value.substring(slash + 1));
            return Float.parseFloat(value);
        } catch (Exception e) {
            return 0f;
        }
    }

    private static long[] parseRange(String value) {
        if (value == null || value.isEmpty()) return new long[]{0L, -1L};
        try {
            int dash = value.indexOf('-');
            if (dash < 0) return new long[]{0L, Long.parseLong(value) - 1L};
            long start = Long.parseLong(value.substring(0, dash).trim());
            long end = Long.parseLong(value.substring(dash + 1).trim());
            return new long[]{start, end};
        } catch (Exception e) {
            return new long[]{0L, -1L};
        }
    }

    private static long parseLong(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static double parseDouble(String value) {
        try {
            return value == null ? 0d : Double.parseDouble(value);
        } catch (Exception e) {
            return 0d;
        }
    }

    private static String attr(Element element, String name, String fallback) {
        if (element == null || !element.hasAttribute(name)) return fallback;
        return element.getAttribute(name);
    }

    private static int attrInt(Element element, String name, int fallback) {
        try {
            return element != null && element.hasAttribute(name) ? Integer.parseInt(element.getAttribute(name)) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long attrLong(Element element, String name, long fallback) {
        try {
            return element != null && element.hasAttribute(name) ? Long.parseLong(element.getAttribute(name)) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String childText(Element parent, String name) {
        Element child = child(parent, name);
        return child == null ? null : child.getTextContent().trim();
    }

    private static Element child(Element parent, String name) {
        for (Element element : children(parent, name)) return element;
        return null;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        if (parent == null) return result;
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            if (name == null || node.getNodeName().equals(name)) result.add((Element) node);
        }
        return result;
    }

    private static final class LocaleHolder {
        private static final java.util.Locale US = java.util.Locale.US;
    }
}
