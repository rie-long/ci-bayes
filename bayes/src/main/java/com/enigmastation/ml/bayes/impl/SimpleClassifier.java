package com.enigmastation.ml.bayes.impl;

import com.enigmastation.ml.bayes.Classifier;
import com.enigmastation.ml.bayes.Feature;
import com.enigmastation.ml.bayes.Tokenizer;
import com.enigmastation.ml.common.collections.MapBuilder;
import com.enigmastation.ml.common.collections.ValueProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SimpleClassifier implements Classifier {
  Map<Object, Feature> features;
  Map<Object, Integer> categories;
  Tokenizer tokenizer = new PorterTokenizer();
  Map<Object, Double> thresholds = new MapBuilder().defaultValue(1.0).build();
  static final ThreadLocal<Object> lastData = new ThreadLocal<>();
  static final ThreadLocal<List<Object>> lastFeatures = new ThreadLocal<>();

  SimpleClassifier(Map<Object, Feature> features, Map<Object, Integer> categories) {
    this.features = features;
    this.categories = categories;
  }

  public SimpleClassifier() {
    features = new MapBuilder().valueProvider(new ValueProvider<Object, Feature>() {

      @Override
      public Feature getDefault(Object k) {
        Feature f = new Feature();
        f.setFeature(k);
        f.setCategories(new MapBuilder().defaultValue(0).<Object, Integer>build());
        return f;
      }

      @Override
      public Map<Object, Feature> load() {
        return null;
      }
    }).build();
    categories = new MapBuilder().defaultValue(0).build();
  }

  @Override
  public Object classify(Object source) {
    return classify(source, "none");
  }

  @Override
  public Object classify(Object source, Object defaultClassification) {
    return classify(source, defaultClassification, 0.0);
  }

  @Override
  public Object classify(Object source, Object defaultClassification, double strength) {
    Map<Object, Double> probabilities = getClassificationProbabilities(source);
    double max = 0.0;
    Object category = null;
    for (Map.Entry<Object, Double> entry : probabilities.entrySet()) {
      if (entry.getValue() > max) {
        max = entry.getValue();
        category = entry.getKey();
      }
    }
    for (Map.Entry<Object, Double> entry : probabilities.entrySet()) {
      if (entry.getKey().equals(category)) {
        continue;
      }

      if ((entry.getValue() * getThreshold(category)) > probabilities.get(category)) {
        return defaultClassification;
      }
    }
    return category;
  }

  @Override
  public Map<Object, Double> getClassificationProbabilities(Object source) {
    Map<Object, Double> probabilities = new HashMap<>();
    for (Object category : categories()) {
      probabilities.put(category, documentProbability(source, category));
    }
    return probabilities;
  }

  @Override
  public void train(Object source, Object classification) {
    List<Object> features = getFeatures(source);
    for (Object feature : features) {
      incrementFeature(feature, classification);
    }
    incrementCategory(classification);
  }

  protected List<Object> getFeatures(Object source) {
    List<Object> features;
    if (source.equals(lastData.get())) {
      features = lastFeatures.get();
    } else {
      features = tokenizer.tokenize(source);
      lastFeatures.set(features);
    }
    lastData.set(source);
    return features;
  }

  private void incrementFeature(Object feature, Object category) {
    Feature f = features.get(feature);
    features.put(feature, f);
    f.incrementCategoryCount(category);
    /*
    Map<Object, Integer> cat = features.get(feature);
    features.put(feature, cat);
    cat.put(category, cat.get(category) + 1);
    */
  }

  private void incrementCategory(Object category) {
    categories.put(category, categories.get(category) + 1);
  }

  // the number of times a feature has occurred in a category
  int featureCount(Object feature, Object category) {
    //if (features.containsKey(feature) && features.get(feature).containsKey(category)) {
    //  return features.get(feature).get(category);
    //}
    return features.get(feature).getCountForCategory(category);
  }

  int categoryCount(Object category) {
    if (categories.containsKey(category)) {
      return categories.get(category);
    }
    return 0;
  }

  int totalCount() {
    int sum = 0;
    for (Integer i : categories.values()) {
      sum += i;
    }
    return sum;
  }

  Set<Object> categories() {
    return categories.keySet();
  }

  double featureProb(Object feature, Object category) {
    if (categoryCount(category) == 0) {
      return 0.0;
    }
    return (1.0 * featureCount(feature, category)) / categoryCount(category);
  }

  double weightedProb(Object feature, Object category, double weight, double assumedProbability) {
    double basicProbability = featureProb(feature, category);
    //System.out.println("basic probability: "+basicProbability);
    double totals = 0;
    for (Object cat : categories()) {
      totals += featureCount(feature, cat);
    }
    //System.out.printf("((%f * %f)+(%f * %f))/(%f + %f) %n", weight,
    //assumedProbability, totals, basicProbability, weight, totals);
    return ((weight * assumedProbability) + (totals * basicProbability)) / (weight + totals);
  }

  double weightedProb(Object feature, Object category, double weight) {
    return weightedProb(feature, category, weight, 0.5);
  }

  double weightedProb(Object feature, Object category) {
    return weightedProb(feature, category, 1.0);
  }

  /* naive bayes, very naive - and not what we usually need. */
  double documentProbability(Object source, Object category) {
    List<Object> features = getFeatures(source);
    double p = 1.0;
    for (Object f : features) {
      p *= weightedProb(f, category);
    }
    return p;
  }

  double prob(Object corpus, Object category) {
    double categoryProbability = (1.0 * categoryCount(category)) / totalCount();
    double documentProbability = documentProbability(corpus, category);
    return documentProbability * categoryProbability;
  }

  public void setThreshold(Object category, Double threshold) {
    thresholds.put(category, threshold);
  }

  public double getThreshold(Object category) {
    return thresholds.get(category);
  }
}
