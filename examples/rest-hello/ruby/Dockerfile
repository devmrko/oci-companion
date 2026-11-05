FROM ruby:3.4-alpine
WORKDIR /app
COPY Gemfile .
RUN bundle install
COPY app.rb .
RUN ruby -c app.rb
USER 10001:10001
EXPOSE 8080
CMD ["bundle", "exec", "ruby", "app.rb"]
